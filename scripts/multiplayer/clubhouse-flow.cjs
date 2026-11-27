const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { once } = require('node:events')
const fs = require('node:fs')
const { Vec3 } = require('vec3')
const { simplify } = require('prismarine-nbt')
const { basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const restarted = process.argv.includes('--after-restart')
const dailyBoard = process.argv.includes('--daily-board')
const report = 'build/clubhouse-install.json'
const players = ['PavilionA', 'PavilionB'].map(connect)
const [a, b] = players
const siteFile = 'plugins/NormalDiscGolf/clubhouse-site.yml'

function pack(state) {
  state.bot._client.on('add_resource_pack', async pack => {
    try {
      const url = new URL(pack.url)
      assert.equal(url.hostname, '127.0.0.1')
      assert.equal(pack.forced, true)
      const response = await fetch(url)
      assert.ok(response.ok, 'the jar automatically serves its local pack')
      assert.equal(createHash('sha1').update(Buffer.from(await response.arrayBuffer())).digest('hex'), pack.hash)
      state.bot._client.write('resource_pack_receive', { uuid:pack.uuid, result:3 })
      state.bot._client.write('resource_pack_receive', { uuid:pack.uuid, result:0 })
      state.packReady = true
    } catch (error) { state.error = error.message }
  })
}
players.forEach(pack)

function coords() {
  const yaml = fs.readFileSync(siteFile, 'utf8')
  assert.match(yaml, /^built: true$/m)
  return new Vec3(...['x','y','z'].map(axis => Number(yaml.match(new RegExp('^'+axis+': (-?\\d+)$', 'm'))[1])))
}

async function menu(state, action, slot, material) {
  const pending = once(state.bot, 'windowOpen', {signal:AbortSignal.timeout(15000)})
  const [window] = (await Promise.all([pending, action()]))[0]
  await until(() => window.slots[slot]?.name === material, 'the expected menu loads')
  return window
}

async function walkTo(state, x, z) {
  const {bot} = state
  const target = new Vec3(x, bot.entity.position.y, z)
  if (bot.entity.position.distanceTo(target) < .55) return
  await bot.waitForChunksToLoad()
  await bot.lookAt(target.offset(0, bot.entity.eyeHeight, 0), true)
  bot.physicsEnabled = true
  bot.setControlState('forward', true)
  try {
    await until(() => Math.hypot(bot.entity.position.x-x, bot.entity.position.z-z) < .55, 'walk to the clubhouse sign')
  } finally {
    bot.clearControlStates()
    await pause(180)
    bot.physicsEnabled = false
  }
}

async function useSign(state, origin, dx, dy, dz) {
  await walkTo(state, origin.x+dx+.5, origin.z+1.5)
  const sign = state.bot.blockAt(origin.offset(dx,dy,dz))
  assert.equal(sign?.name, 'spruce_sign')
  await state.bot.activateBlock(sign)
}

async function putt(state, target) {
  const {bot} = state
  const shot = await basketShot(bot, 'putting', target)
  assert.ok(shot, 'real eight-block trial has an open putting line')
  await bot.lookAt(shot.aim, true)
  let locked = false, released = false
  const onMeter = bar => {
    const title = String(bar.title)
    if (!locked && title.startsWith('Putt Power:') && Math.round(bar.health * 100) === shot.power) {
      locked = true
      bot.swingArm('right')
    } else if (locked && !released && title.startsWith('Release Timing:') && Math.abs(bar.health-.5)<.02) {
      released = true
      bot.swingArm('right')
    }
  }
  const before = state.messages.length
  bot.on('bossBarUpdated', onMeter)
  try {
    await until(() => state.messages.slice(before).includes('Practice basket! Aim again from the tee.'), 'real throw sinks in the practice basket', 24000)
  } finally {
    bot.off('bossBarUpdated', onMeter)
  }
  assert.ok(state.messages.slice(before).includes('Practice disc released.'))
  assert.ok(!state.messages.slice(before).some(line => line.startsWith('Skill XP:') || line.startsWith('Round reward:')))
}

async function checkDailyBoard(origin) {
  const pos = origin.offset(10,1,4)
  let board = ''
  const receive = nbt => { board = JSON.stringify(simplify(nbt)?.front_text?.messages) }
  b.bot._client.on('map_chunk', chunk => {
    if (chunk.x !== (pos.x >> 4) || chunk.z !== (pos.z >> 4)) return
    const sign = chunk.blockEntities.find(entity => entity.x === (pos.x & 15)
      && entity.y === pos.y && entity.z === (pos.z & 15))
    if (sign) receive(sign.nbtData)
  })
  b.bot._client.on('tile_entity_data', update => {
    const {x,y,z} = update.location
    if (x === pos.x && y === pos.y && z === pos.z) receive(update.nbtData)
  })

  await command(b, 'clubhouse visit', /^Parkland Clubhouse/)
  await useSign(b,origin,8,2,3)
  await until(() => board?.includes('No scores yet'), 'observer sees the empty physical board')
  const start = a.messages.length
  await command(a, 'daily play', /queued for Daily Course/)
  for (const hole of [1,2]) {
    await until(() => a.turns.length >= hole && a.bot.game.gameMode === 'adventure', 'the player takes the Daily tee', 120000)
    if (hole === 1) await command(a, 'shot timing on', /timing on/)
    await command(a, 'lie')
    await pause(225)
    let pin
    await until(() => (pin = a.bot.findBlock({matching:a.bot.registry.blocksByName.yellow_concrete.id,maxDistance:65})),
      'the Daily basket reaches the client')
    const shot = await basketShot(a.bot,'driver',pin.position.offset(.5,-1.1,.5))
    assert.ok(shot, 'the Daily drive has an open line')
    await release(a,shot)
    await until(() => a.messages.slice(start).some(line=>line.startsWith(a.name+' completed hole '+hole+'!')), 'Daily basket counted')
  }
  await until(() => a.messages.slice(start).includes('Round complete!'), 'Daily round completes')
  await until(() => board?.includes('1 Pavilion 2'), 'observer sees the physical board update with the real posted score')
  console.log('PASS: a completed two-hole Daily posted a new result to a second player watching the physical clubhouse board.')
}

async function main() {
  if (dailyBoard) {
    await until(() => players.every(state => state.spawned), 'two players join the leaderboard fixture', 30000)
    await checkDailyBoard(coords())
    return
  }
  await until(() => players.every(state => state.spawned && state.packReady), 'default pack download and version 1.21.8 login', 30000)
  const origin = coords()
  if (restarted) {
    const previous = JSON.parse(fs.readFileSync(report, 'utf8'))
    assert.deepEqual(origin.toArray(), previous.origin)
    await command(a, 'clubhouse visit', /^Parkland Clubhouse/)
    await until(() => a.bot.entity.position.distanceTo(origin.offset(.5,1,1.5)) < 1, 'persistent clubhouse entrance')
    const rack = await menu(a, () => useSign(a,origin,-8,2,3), 4, 'clock')
    assert.deepEqual(rack.slots.slice(5,8).map(item => item?.name), ['slime_ball','heart_of_the_sea','fire_charge'])
    console.log('PASS: one clubhouse site, active counters, and the same default pack after restarting Paper.')
    return
  }

  const roundPicker = await menu(a, () => command(a,'menu'), 4, 'spruce_sign')
  const clubhouse = await menu(a, () => a.bot.clickWindow(4,0,0), 37, 'spruce_sign')
  assert.equal(clubhouse.slots[39]?.name,'clock')
  assert.equal(clubhouse.slots[41]?.name,'target')
  assert.equal(roundPicker.slots[13]?.name,'gold_ingot')
  await a.bot.clickWindow(37,0,0)
  await until(() => a.bot.entity.position.distanceTo(origin.offset(.5,1,1.5)) < 1, 'clubhouse opens and teleports to its entrance')
  await command(b, 'clubhouse visit', /^Parkland Clubhouse/)

  const initial = new Map(players.map(state => [state, inventory(state.bot)]))
  await command(a, 'queue match', /queued for Match Play/)
  await command(a, 'status', /You are queued for Match Play|Your round is preparing/)
  const rack = await menu(a, () => useSign(a,origin,-8,2,3), 4, 'clock')
  assert.deepEqual(rack.slots.slice(5,8).map(item => item?.name), ['slime_ball','heart_of_the_sea','fire_charge'])
  assert.match(JSON.stringify(rack.slots[4].components), /Next restock/)
  await a.bot.clickWindow(5,0,0)
  await until(() => rack.slots[33]?.name==='target', 'an unowned offer can be tried from the rack')
  await a.bot.clickWindow(33,0,0)
  await until(() => a.messages.some(line=>/^Trying .* from 8 blocks\.$/.test(line)), 'selecting the offer starts a loaner trial')
  await until(() => a.bot.entity.position.distanceTo(origin.offset(.5,1,12.5)) < 1, 'trial moves the player to the middle tee')
  await putt(a, origin.offset(.5,1.9,20.5))
  await command(a, 'status', /You are queued for Match Play|Your round is preparing/)
  assert.deepEqual(inventory(a.bot), initial.get(a), 'practice preserves the player inventory')
  assert.equal(a.bot.game.gameMode, a.original.mode)

  const beforeBoard = b.messages.length
  await useSign(b,origin,8,2,3)
  await until(() => b.messages.slice(beforeBoard).some(line=>line.startsWith('Daily leaderboard (')), 'Daily sign opens the live leaderboard')
  await walkTo(b,origin.x+.5,origin.z+1.5)
  const practice = await menu(b, () => useSign(b,origin,0,2,5), 4, 'target')
  assert.ok(practice.slots[10], 'the putting green has a playable catalog')
  b.bot.closeWindow(practice)

  await command(a, 'practice tee 12', /from 12 blocks/)
  await command(b, 'queue match', /queued for Match Play/)
  await until(() => players.every(state=>state.messages.some(line=>line.startsWith('Match Play:'))
    && state.bot.game.gameMode==='adventure'), 'queue starts a real Match Play round and closes the practice meter', 120000)
  await command(a, 'practice', /Finish or leave your round/)
  await command(a, 'practice leave', /Choose a trial disc/)
  await command(b, 'quit', /forfeited/)
  await until(() => players.every(state=>state.bot.game.gameMode===state.original.mode), 'both players return from the match')
  for (const state of players) assert.deepEqual(inventory(state.bot), initial.get(state))

  fs.writeFileSync(report, JSON.stringify({origin:origin.toArray()}))
  console.log('PASS: one-jar startup, local pack, clickable rotating shop and Daily board, loaner basket, unchanged queue, and clean round handoff.')
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(() => {
  for (const {bot} of players) bot.quit()
})
