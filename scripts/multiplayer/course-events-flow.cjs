const assert = require('node:assert/strict')
const { until, pause, connect, inventory, command, shortShot, release } = require('./parkland-41.cjs')

const players = ['A', 'B'].map(side => connect('Weather' + side + process.pid % 10000))
const [leader, partner] = players
const active = () => players.find(state => state.name === leader.turns.at(-1))

for (const state of players) {
  const { bot } = state
  const visuals = state.visuals = { rain: 0, thunder: 0, flashes: 0, darkness: false, mist: 0, displays: new Map() }
  bot._client.on('game_state_change', ({ reason, gameMode }) => {
    if (reason === 'rain_level_change') visuals.rain = gameMode
    if (reason === 'thunder_level_change') visuals.thunder = gameMode
  })
  bot._client.on('spawn_entity', ({ entityId, type }) => {
    if (type === bot.registry.entitiesByName.lightning_bolt.id) visuals.flashes++
    if (type === bot.registry.entitiesByName.item_display.id) visuals.displays.set(entityId, {})
  })
  bot._client.on('entity_metadata', ({ entityId, metadata }) => {
    const display = visuals.displays.get(entityId)
    if (!display) return
    const keys = bot.registry.entitiesByName.item_display.metadataKeys
    for (const entry of metadata) display[keys[entry.key]] = entry.value
  })
  bot._client.on('entity_effect', ({ entityId, effectId }) => {
    if (entityId === bot.entity?.id && effectId === bot.registry.effectsByName.Darkness.id) visuals.darkness = true
  })
  bot._client.on('remove_entity_effect', ({ entityId, effectId }) => {
    if (entityId === bot.entity?.id && effectId === bot.registry.effectsByName.Darkness.id) visuals.darkness = false
  })
  bot._client.on('world_particles', ({ particle }) => {
    if (particle.type === 'cloud') visuals.mist++
  })
}

async function conditions(hole, event) {
  await until(() => players.every(({ messages, bot }) =>
    messages.includes('Course event: ' + event) && bot.game.gameMode === 'adventure'), event + ' and completed flyover', 120000)
  for (const state of players) {
    const mark = state.messages.length
    await command(state, 'status', new RegExp('^Round: ndg_'))
    const status = state.messages.slice(mark)
    assert.ok(status.includes('Hole: ' + hole + '/4'), 'current hole in status')
    assert.ok(status.includes('Course event: ' + event), 'current event in status')
  }
}

async function playThrows(event) {
  const played = new Set()
  for (let count = 0; count < players.length; count++) {
    await until(() => active() && !played.has(active()), 'each player gets a turn')
    const state = active()
    played.add(state)
    await command(state, 'lie')
    await pause(230)
    const mark = state.messages.length
    const turns = leader.turns.length
    const start = state.bot.entity.position
    await release(state, shortShot(state.bot, 35, -.20, start.offset(0, 0, 8)), 2)
    assert.ok(state.messages.slice(mark).some(line => line.startsWith('Skill XP:')), event + ' throw settled')
    await until(() => leader.turns.length > turns, 'turn advances after the shot')
  }
}

async function concedeHole() {
  for (let turn = 0; turn < players.length; turn++) {
    const count = leader.turns.length
    await command(active(), 'concede', /^Hole conceded:/)
    await until(() => leader.turns.length > count || leader.messages.includes('Round complete!'), 'hole advances')
  }
}

async function restored() {
  await until(() => players.every(({ bot, original, visuals }) =>
    bot.game.gameMode === original.mode && !visuals.darkness
    && bot.entity.position.distanceTo(original.position) < 2), 'lobby state restored')
  for (const { bot, original } of players) assert.deepEqual(inventory(bot), original.items)
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients join')
  await command(leader, 'party invite ' + partner.name, /Party invite sent/)
  await command(partner, 'party accept', /joined the party/)
  await command(leader, 'queue arcade', /queued for Arcade/)
  await conditions(1, 'Thunderstorm')
  for (const state of players) await command(state, 'shot timing on', /timing on/)
  await until(() => players.every(({ visuals }) => visuals.rain > .5 && visuals.thunder > .5 && visuals.flashes > 0),
    'both clients receive rain, thunder and cosmetic lightning')
  for (const state of players) await command(state, 'shot wind', /^Wind: shifting storm gusts/)
  await playThrows('Thunderstorm')
  console.log('PASS: two clients received storm weather and lightning and made throws under shifting winds')
  await concedeHole()

  await conditions(2, 'Fog')
  await until(() => players.every(({ visuals }) => visuals.darkness && visuals.mist > 0
    && visuals.rain < .01 && visuals.thunder < .01), 'weather clears and both clients receive the fog effect and mist')
  await playThrows('Fog')
  console.log('PASS: storm weather cleared, fog reduced visibility for both clients, and each threw in fog')
  await concedeHole()

  await conditions(3, 'Night Golf')
  await until(() => players.every(({ bot, visuals }) => bot.time.timeOfDay === 18000 && !visuals.darkness),
    'both clients receive midnight and fog removal')
  const lampPosition = active().bot.entity.position.offset(0, 2.1, 0).floored()
  await until(() => players.every(({ bot }) => bot.blockAt(lampPosition)?.name === 'light'), 'both clients receive the tee light')
  for (const { visuals } of players) visuals.displays.clear()
  await playThrows('Night Golf')
  assert.ok(players.every(({ visuals }) => [...visuals.displays.values()].some(display =>
    (display.shared_flags & 0x40) !== 0 && display.brightness_override > 0)),
  'both clients receive a glowing, fully lit flying disc')
  console.log('PASS: fog cleared, both clients received midnight, a tee light and glowing discs')
  await concedeHole()

  await conditions(4, 'Clear')
  await until(() => players.every(({ bot }) => bot.time.timeOfDay === 6000
    && bot.blockAt(lampPosition)?.name !== 'light'), 'daylight returns and the tee light is removed')
  await playThrows('Clear')
  await concedeHole()
  await restored()
  assert.ok(players.every(state => state.messages.includes('Final standings:')), 'both players receive standings')
  console.log('PASS: daylight and normal play returned, then round completion restored both players')

  const secondRound = leader.turns.length
  await command(leader, 'queue ffa', /queued for FFA/)
  await until(() => leader.turns.length > secondRound && players.every(({ bot }) => bot.game.gameMode === 'adventure'),
    'rated round finishes its flyover', 120000)
  await conditions(1, 'Clear')
  await until(() => players.every(({ bot, visuals }) => bot.time.timeOfDay === 6000
    && !visuals.darkness && visuals.rain < .01 && visuals.thunder < .01), 'default rated opt-out')
  for (const state of players) await command(state, 'quit', /forfeited/)
  await restored()
  console.log('PASS: rated FFA kept clear conditions with the same configured sequence')
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  for (const { bot } of players) bot.quit()
  await pause(600)
  process.exit(process.exitCode || 0)
})
