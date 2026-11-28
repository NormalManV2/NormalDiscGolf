const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { Vec3 } = require('vec3')
const { until, pause, connect, inventory, command: sendCommand, basketShot, release } = require('./parkland-41.cjs')

const courseId = process.env.NDG_TEST_CUSTOM_COURSE || 'builder-meadow'
const courseName = 'Builder Meadow'
const afterRestart = process.argv.includes('--after-restart')
const players = ['A', 'B'].map(side => connect('Builder' + side + process.pid % 10000))
const [builder, guest] = players
const legacyPack = process.argv.includes('--expect-legacy-pack')
const modernPack = process.argv.includes('--expect-modern-pack')
const verifyPack = legacyPack || modernPack
const active = () => players.find(state => state.name === builder.turns.at(-1))
const horizontalDistance = (a, b) => Math.hypot(a.x-b.x, a.z-b.z)

async function command(state, text, response) {
  if (verifyPack) await pause(650)
  return sendCommand(state, text, response)
}

if (verifyPack) for (const state of players) {
  const { bot } = state
  state.displayStarts = new Map()
  state.animatedDiscs = new Set()
  bot.on('entitySpawn', entity => {
    if (entity.name === 'item_display') state.displayStarts.set(entity.id, entity.position.clone())
  })
  bot.on('entityMoved', entity => {
    const start = state.displayStarts.get(entity.id)
    if (start && entity.position.distanceTo(start) > 1) state.animatedDiscs.add(entity.id)
  })
  bot._client.on(legacyPack ? 'resource_pack_send' : 'add_resource_pack', async pack => {
    try {
      assert.equal(pack.forced, true)
      const response = await fetch(pack.url)
      assert.ok(response.ok)
      const bytes = Buffer.from(await response.arrayBuffer())
      assert.equal(createHash('sha1').update(bytes).digest('hex'), pack.hash)
      assert.ok(bytes.includes(legacyPack ? 'assets/minecraft/models/item/fire_charge.json'
        : 'assets/ndg/items/starter-driver.json'))
      const reply = result => bot._client.write('resource_pack_receive', { uuid: pack.uuid, result })
      reply(3)
      if (modernPack) reply(4)
      reply(0)
      state.packLoaded = true
    } catch (error) { state.packError = error }
  })
}

function readPoint(line, label) {
  const match = line.match(new RegExp(label + ' (-?\\d+), (-?\\d+), (-?\\d+)'))
  assert.ok(match, 'parse placed anchor in ' + line)
  return new Vec3(Number(match[1]) + .5, Number(match[2]), Number(match[3]) + .5)
}

async function walk(state, dx) {
  const { bot } = state
  const target = bot.entity.position.offset(dx, 0, 0)
  await bot.waitForChunksToLoad()
  bot.physicsEnabled = true
  try {
    await bot.lookAt(target.offset(0, bot.entity.eyeHeight, 0), true)
    bot.setControlState('forward', true)
    await until(() => horizontalDistance(bot.entity.position, target) < .55, 'builder walks to the next anchor')
  } finally {
    bot.clearControlStates()
    await pause(350)
    bot.physicsEnabled = false
  }
}

async function buildCourse() {
  await walk(builder, 2)
  await command(builder, 'course create ' + courseId + ' ' + courseName, /^Started course draft/)
  await command(builder, 'course save', /^Add at least one hole\.$/)
  await command(builder, 'course tee 1', /^Placed hole 1 tee at/)
  await command(builder, 'course basket 1', /^Placed hole 1 basket at/)
  await command(builder, 'course save', /leave at least 5 horizontal blocks/)
  assert.ok(builder.messages.includes('Hole 1: set a par from 2 to 6.'))
  await command(guest, 'course play ' + courseId, /^No saved course uses that ID/)
  await command(builder, 'course par 1 8', /^Par must be from 2 to 6\.$/)
  await command(builder, 'course par 1 3', /^Set hole 1 par to 3/)
  await walk(builder, 15)
  await command(builder, 'course basket 1', /^Placed hole 1 basket at/)
  await walk(builder, 5)
  await command(builder, 'course tee 2', /^Placed hole 2 tee at/)
  await command(builder, 'course save', /^Hole 2: basket is missing\.$/)
  await walk(builder, 14)
  await command(builder, 'course basket 2', /^Placed hole 2 basket at/)
  await command(builder, 'course par 2 4', /^Set hole 2 par to 4/)
  await command(builder, 'course preview', /^Draft markers shown/)
  await command(builder, 'course validate', /^Course is valid\. 2 hole\(s\), par 7\.$/)
  await command(builder, 'course save', /^Saved custom course/)
  await command(guest, 'course edit ' + courseId, /^Only the course owner or an administrator can edit it\.$/)
  await command(builder, 'course edit ' + courseId, /^Editing course/)
  await command(builder, 'course par 2 5', /^Set hole 2 par to 5/)
  await command(builder, 'course save', /^Saved custom course/)
  console.log('PASS: ordinary builder placed anchors, rejected incomplete and overlapping layouts, saved and revised the course')
}

async function verifyCatalog() {
  await command(guest, 'course list', new RegExp('^' + courseId + ' .*par 8$'))
  await command(guest, 'course validate ' + courseId, /^Course is valid\. 2 hole\(s\), par 8\.$/)
  const start = guest.messages.length
  await command(guest, 'course info ' + courseId, /^Hole 2 .*Par 5/)
  return [1,2].map(hole => {
    const line = guest.messages.slice(start).find(message => message.startsWith('Hole ' + hole + ' '))
    return {tee:readPoint(line, 'Tee'), basket:readPoint(line, 'Basket').offset(0, .9, 0)}
  })
}

async function playCourse(holes) {
  const originals = players.map(({ bot }) => ({ position:bot.entity.position.clone(), mode:bot.game.gameMode, items:inventory(bot) }))
  await command(builder, 'party invite ' + guest.name, /Party invite sent/)
  await command(guest, 'party accept', /joined the party/)
  await command(guest, 'course play ' + courseId, /^Only the party leader/)
  await command(builder, 'course play ' + courseId, /^Queued for custom course Builder Meadow\.$/)
  await until(() => active() && players.every(state => state.bot.game.gameMode === 'adventure'), 'the custom round starts', 45000)
  for (const state of players) {
    await command(state, 'status', /^Round: ndg_custom_/)
    assert.ok(state.messages.includes(courseName + ' (custom course: ' + courseId + ')'))
    await command(state, 'shot timing on', /timing on/)
    await command(state, 'replay off', /^Instant replays: off/)
    if (legacyPack) await until(() => /CustomModelData/.test(JSON.stringify(state.bot.inventory.slots[38]?.nbt)),
      'the legacy driver carries its resource-pack model')
    if (modernPack) await until(() => /ndg:starter-driver/.test(JSON.stringify(state.bot.inventory.slots[38]?.components)),
      'the driver carries its namespaced resource-pack model')
  }
  for (const [index, hole] of holes.entries()) {
    const number = index + 1
    await until(() => players.every(({ bot }) =>
      Object.values(bot.entities).some(entity => entity.name === 'block_display'
        && horizontalDistance(entity.position, hole.basket) < 1)), 'both clients receive the active basket model')
    for (const state of players) await command(state, 'status', new RegExp('^Hole: ' + number + '/2$'))
    const finished = state => builder.messages.some(line => line.startsWith(state.name + ' completed hole ' + number + '!'))
    for (let attempt=0;attempt<6 && !players.every(finished);attempt++) {
      await until(() => active() && !finished(active()), 'an unfinished player has this custom hole')
      const turn = active(), before = builder.turns.length
      await command(turn, 'lie')
      await pause(220)
      if (attempt === 0) assert.ok(turn.bot.entity.position.distanceTo(hole.tee) < .5, 'the round uses the saved tee')
      const shot = await basketShot(turn.bot, 'driver', hole.basket)
      assert.ok(shot, 'a scoring line from ' + turn.bot.entity.position + ' to ' + hole.basket)
      await release(turn, shot)
      await until(() => players.every(finished) || builder.turns.length > before, 'scoring advances the turn')
    }
    assert.ok(players.every(finished), 'both players hole out on custom hole ' + number)
  }
  await until(() => players.every(state => state.messages.includes('Round complete!')), 'both players receive final results')
  await until(() => players.every(({ bot }, index) => bot.game.gameMode === originals[index].mode
    && bot.entity.position.distanceTo(originals[index].position) < .75
    && !Object.values(bot.entities).some(entity => entity.name === 'block_display')), 'original positions and game modes restored and temporary baskets removed')
  for (const [index, state] of players.entries()) {
    assert.deepEqual(inventory(state.bot), originals[index].items)
    assert.ok(state.messages.includes('Final standings:'))
    assert.ok(state.messages.includes('Round reward: +20 credits. Balance: 20. Open /ndg shop.'))
    assert.ok(!state.messages.some(line => line.startsWith('Round rating:')))
    if (verifyPack) assert.ok(state.animatedDiscs.size >= 4, 'the client received a moving display entity for each scoring throw')
  }
  console.log('PASS: both players holed out on both saved holes, received standings and rewards, and returned to their original state')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Minecraft Java clients connect')
  if (verifyPack) {
    await until(() => {
      for (const state of players) if (state.packError) throw state.packError
      return players.every(state => state.packLoaded)
    }, 'both clients download the resource pack')
    console.log('PASS: both clients downloaded the required pack and verified its checksum and disc assets')
  }
  await pause(550)
  if (!afterRestart) await buildCourse()
  const holes = await verifyCatalog()
  if (afterRestart) console.log('PASS: saved anchors and updated pars loaded after a server restart')
  await playCourse(holes)
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  for (const { bot } of players) bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
