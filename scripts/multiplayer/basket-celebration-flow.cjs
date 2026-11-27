const assert = require('node:assert/strict')
const { Vec3 } = require('vec3')
const { basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const chain = ['block.chain.hit', 'block.chain.fall', 'block.iron_trapdoor.close']
const aceChime = 'block.note_block.chime'

function observe(state) {
  const events = []
  state.bot._client.on('sound_effect', ({sound}) => {
    const name = sound.data?.soundName?.replace(/^minecraft:/, '') ?? state.bot.registry.sounds[sound.soundId + 1]?.name
    if (chain.includes(name) || name === aceChime) events.push(name)
  })
  state.bot._client.on('world_particles', ({particle}) => {
    if (particle.type === 'firework') events.push('firework')
  })
  state.bot._client.on('set_title_text', ({text}) => {
    const title = JSON.stringify(text)
    if (title.includes('Ace!')) events.push('ace title')
    if (title.includes('Instant replay')) events.push('replay title')
  })
  return events
}

async function makeCourse(builder) {
  const id = 'chain-test-' + process.pid
  const { bot } = builder
  await command(builder, 'course create ' + id + ' Chain Test', /Started course draft/)
  await command(builder, 'course tee 1', /Placed hole 1 tee at/)
  const tee = bot.entity.position.clone()
  await bot.waitForChunksToLoad()
  bot.physicsEnabled = true
  await bot.lookAt(tee.offset(10, bot.entity.eyeHeight, 0), true)
  bot.setControlState('forward', true)
  try {
    await until(() => bot.entity.position.x - tee.x > 8, 'walk to a short putting target')
  } finally {
    bot.clearControlStates()
    await pause(350)
    bot.physicsEnabled = false
  }
  const stamp = builder.messages.length
  await command(builder, 'course basket 1', /Placed hole 1 basket at/)
  await command(builder, 'course par 1 3', /Set hole 1 par to 3/)
  await command(builder, 'course save', /Saved custom course/)
  const coordinates = builder.messages.slice(stamp).find(line => line.includes('Placed hole 1 basket at'))
    .match(/(-?\d+), (-?\d+), (-?\d+)/).slice(1).map(Number)
  const basket = new Vec3(...coordinates).offset(.5,.9,.5)
  return {id,basket}
}

async function aimPutt(state, basket) {
  await command(state, 'lie')
  await pause(230)
  const shot = await basketShot(state.bot, 'putting', basket)
  assert.ok(shot, 'short fixture has a playable putt')
  await release(state, shot, 0)
}

async function main() {
  const [leader, partner, outsider] = ['A','B','C'].map(side => connect('Chains'+side+process.pid%10000))
  const players = [leader, partner]
  const all = [...players, outsider]
  const records = new Map(all.map(state => [state, observe(state)]))
  const active = () => players.find(state => state.name === leader.turns.at(-1))
  try {
    await until(() => all.every(state => state.spawned), 'three clients connect to the temporary server')
    const {id,basket} = await makeCourse(leader)
    const original = new Map(players.map(state => [state, state.bot.entity.position.clone()]))
    await command(leader, 'party invite '+partner.name, /Party invite sent/)
    await command(partner, 'party accept', /joined the party/)
    await command(leader, 'course play '+id, /Queued for custom course/)
    await until(() => active() && players.every(state => state.bot.game.gameMode==='adventure'), 'the two-player card starts')
    for (const state of players) {
      await command(state, 'shot timing on', /timing on/)
      await command(state, 'shot stance putt', /putt stance/)
    }

    const ace = active(), next = players.find(state => state !== ace)
    await aimPutt(ace, basket)
    await until(() => players.every(state => records.get(state).includes('replay title')), 'ace replay follows the live celebration')
    for (const state of players) {
      const cues = records.get(state)
      assert.deepEqual(cues.filter(event => chain.includes(event)), chain, 'both players hear all three chain layers in order')
      assert.ok(cues.includes(aceChime) && cues.includes('firework'), 'both players receive the chime and sparks')
      assert.ok(cues.indexOf('ace title') >= 0 && cues.indexOf('ace title') < cues.indexOf('replay title'), 'the ace callout precedes replay')
    }
    assert.ok(!records.get(outsider).some(event => event === 'ace title' || event === aceChime), 'only the card receives the ace callout')
    await until(() => active() === next && players.every(state => state.bot.game.gameMode==='adventure'), 'next turn resumes after replay')

    await command(next, 'lie')
    await pause(230)
    const eye = next.bot.entity.position.offset(0, next.bot.entity.eyeHeight, 0)
    const turns = leader.turns.length
    await release(next, { power:25, aim:eye.offset(0,-4,3) }, 0)
    await until(() => leader.turns.length > turns, 'a missed first putt resolves')
    assert.equal(records.get(next).filter(event => event === 'firework').length, 1, 'the miss does not celebrate')

    const marks = players.map(state => records.get(state).length)
    await aimPutt(next, basket)
    await until(() => players.every(state => state.messages.includes('Round complete!')), 'the regular basket advances to standings')
    for (const [index, state] of players.entries()) {
      const cues = records.get(state).slice(marks[index])
      assert.deepEqual(cues.filter(event => chain.includes(event)), chain, 'a normal basket plays the full chain sound')
      assert.ok(!cues.some(event => event === 'ace title' || event === aceChime || event === 'firework'), 'a normal basket keeps the ace cues exclusive')
    }
    await until(() => players.every(state => state.bot.game.gameMode === state.original.mode
      && state.bot.entity.position.distanceTo(original.get(state)) < 1), 'lobby locations and modes restored')
    for (const state of players) assert.deepEqual(inventory(state.bot), state.original.items)
    console.log('PASS: two-player ace and regular basket, three chain layers, private ace callout, replay ordering, and round restoration.')
  } finally {
    for (const {bot} of all) bot.quit()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
