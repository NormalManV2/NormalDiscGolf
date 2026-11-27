const assert = require('node:assert/strict')
const { once } = require('node:events')
const { simplify } = require('prismarine-nbt')
const { basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const players = ['PolishA', 'PolishB'].map(connect)
const [a, b] = players
const shotBar = bot => Object.values(bot.bossBars).find(bar => /^(Throw|Putt) Power:|^Release Timing:/.test(String(bar.title)))
const cancelled = /^Aim cancelled\. Your lie and turn are unchanged\.$/

for (const state of players) {
  state.rows = new Map()
  state.resets = []
  state.bot._client.on('add_resource_pack', pack => {
    state.bot._client.write('resource_pack_receive', { uuid: pack.uuid, result: 3 })
    state.bot._client.write('resource_pack_receive', { uuid: pack.uuid, result: 0 })
  })
  state.bot._client.on('teams', packet => {
    if (packet.team.startsWith('ndg') && packet.prefix)
      state.rows.set(packet.team, JSON.stringify(simplify(packet.prefix)))
  })
  state.bot._client.on('reset_score', packet => {
    if (packet.objective_name === 'ndg') state.resets.push(packet)
  })
}

async function windowFrom(state, open, slot, material) {
  const window = once(state.bot, 'windowOpen', {signal:AbortSignal.timeout(15000)})
  await Promise.all([window, open()])
  await until(() => state.bot.currentWindow?.slots[slot]?.name === material, 'menu contains ' + material)
}

async function main() {
  await until(() => players.every(state => state.spawned && state.messages.includes('Parkland disc models are ready.')),
    'both 1.21.8 clients join and apply the pack', 30000)
  await windowFrom(a, () => command(a, 'menu'), 8, 'written_book')
  await windowFrom(a, () => a.bot.clickWindow(8,0,0), 10, 'slime_ball')
  assert.equal(a.bot.currentWindow.slots[24].name, 'clock')
  await windowFrom(a, () => a.bot.clickWindow(20,0,0), 4, 'target')
  await a.bot.clickWindow(10,0,0)
  await until(() => shotBar(a.bot), 'guide shortcut opens a loaner aim meter')
  await command(a, 'shot cancel', cancelled)
  await until(() => !shotBar(a.bot), 'practice cancel removes the boss bar')
  await command(a, 'shot cancel', /^You are not aiming a throw\.$/)
  assert.deepEqual(inventory(a.bot), a.original.items)

  for (const state of players) await command(state, 'queue ffa', /^You have been queued for FFA\.$/)
  await until(() => a.turns.length && players.every(state => state.bot.game.gameMode === 'adventure'
    && state.rows.get('ndg0')?.includes('Hole 1 / 2')), 'the FFA round starts and both clients see its sidebar', 90000)
  const active = () => players.find(state => state.name === a.turns.at(-1))
  const first = active()
  await command(first, 'shot timing on', /timing on/)
  first.bot.setQuickBarSlot(2)
  await pause(250)
  const before = inventory(first.bot)
  first.bot.activateItem()
  await until(() => shotBar(first.bot), 'round power meter is open')
  first.bot.swingArm('right')
  await until(() => String(shotBar(first.bot)?.title).startsWith('Release Timing:'), 'power is locked')
  const messages = first.messages.length
  first.bot._client.write('block_dig', {status:4, location:{x:0,y:0,z:0}, face:0, sequence:0})
  await until(() => first.messages.slice(messages).some(line => cancelled.test(line)) && !shotBar(first.bot),
    'the drop key cancels timed release')
  assert.deepEqual(inventory(first.bot), before, 'the drop key keeps the disc in the inventory')
  assert.equal(active(), first, 'the same player keeps the turn')
  await command(first, 'score text', /^Current hole strokes: 0$/)

  for (const state of players) await command(state, 'shot timing on', /timing on/)
  for (const hole of [1,2]) {
    await until(() => players.every(state => state.rows.get('ndg0')?.includes('Hole '+hole+' / 2')),
      'both clients see the current hole in the same sidebar row')
    const finished = new Set()
    while (finished.size < players.length) {
      await until(() => active() && !finished.has(active()) && active().bot.game.gameMode === 'adventure',
        'an unfinished player takes the tee')
      const state = active()
      const turn = a.turns.length
      let pin
      await command(state, 'lie')
      await pause(225)
      await until(() => (pin = state.bot.findBlock({
        matching:state.bot.registry.blocksByName.yellow_concrete.id,maxDistance:65
      })), 'the basket is loaded')
      const shot = await basketShot(state.bot, 'driver', pin.position.offset(.5,-1.1,.5))
      assert.ok(shot, 'the fixture offers a playable line')
      await release(state, shot)
      await until(() => a.messages.some(line => line.startsWith(state.name+' completed hole '+hole+'!')),
        'the basket counts for the player')
      finished.add(state)
      if (hole === 1 || finished.size < players.length)
        await until(() => a.turns.length > turn, 'the round advances')
    }
  }
  await until(() => players.every(state => state.messages.includes('Round complete!') && state.bot.game.gameMode === state.original.mode),
    'both players complete the round and return to the lobby')
  for (const state of players) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    assert.equal(state.resets.length, 0, 'the sidebar retains its rows during timer and scoring updates')
  }
  console.log('PASS: guide navigation, cancel in practice and during release, stable sidebar rows, and a complete two-client FFA round.')
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(() => {
  for (const {bot} of players) bot.quit()
})
