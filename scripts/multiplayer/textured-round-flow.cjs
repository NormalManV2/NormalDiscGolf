const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { inspect } = require('node:util')
const { simplify } = require('prismarine-nbt')
const { basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const players = ['RoundTextureA', 'RoundTextureB'].map(connect)
const [a, b] = players
for (const state of players) {
  state.rows = new Map()
  state.windows = []
  state.bot._client.on('add_resource_pack', pack => { state.pack = pack })
  state.bot._client.on('teams', packet => {
    if (packet.team.startsWith('ndg') && packet.prefix) state.rows.set(packet.team, JSON.stringify(simplify(packet.prefix)))
  })
  state.bot._client.on('open_window', packet => state.windows.push(JSON.stringify(simplify(packet.windowTitle))))
}
const active = () => players.find(state => state.name === a.turns.at(-1))
const slot = (state, index) => state.bot.currentWindow?.slots[index]
const detail = (state, index) => inspect(slot(state, index)?.components, {depth:12})
const tagged = state => /[\ue750-\ue757]/.test(state.rows.get('ndg0'))
const painted = state => /[\ue73a\ue73b]/.test(state.windows.at(-1))

async function menu(state, name, title) {
  const before = state.windows.length
  await command(state, name)
  await until(() => state.windows.length > before && state.windows.at(-1).includes(title)
    && Boolean(slot(state,4)), title + ' opens with its main tile')
  assert.equal(painted(state), state === a, 'the textured frame is sent only to the pack user')
}

async function main() {
  await until(() => players.every(state => state.spawned && state.pack), 'both clients join and receive the optional pack', 30000)
  const bytes = Buffer.from(await (await fetch(a.pack.url)).arrayBuffer())
  assert.equal(createHash('sha1').update(bytes).digest('hex'), a.pack.hash)
  for (const asset of ['gui/hud/hole.png', 'gui/panel/scorecard.png', 'gui/panel/summary.png',
    'items/gui/under_par.json', 'items/gui/fairway.json']) assert.ok(bytes.includes(asset), asset)
  for (const [index, state] of players.entries()) {
    for (const result of index === 0 ? [3,4,0] : [1])
      state.bot._client.write('resource_pack_receive', { uuid: state.pack.uuid, result })
  }
  await until(() => a.messages.includes('Parkland disc models are ready.')
    && b.messages.some(line => line.startsWith('Using vanilla disc icons.')), 'the two clients use different settings')
  for (const state of players) await command(state, 'queue ffa', /^You have been queued for FFA\.$/)
  await until(() => active() && players.every(state => state.bot.game.gameMode === 'adventure'
    && state.rows.get('ndg0')?.includes('Hole 1 / 2')), 'both clients see live round HUD', 120000)
  assert.equal(tagged(a), true)
  assert.equal(tagged(b), false)

  for (const state of players) {
    await command(state, 'shot timing on', /timing on/)
    await menu(state, 'score', 'Round Scorecard')
    await until(() => detail(state,9).includes('0 strokes so far'), 'the current hole tracks strokes')
    assert.equal(detail(state,9).includes('ndg:gui/even_par'), state === a)
    assert.equal(slot(state,10).count, 2, 'hole tiles are numbered')
    assert.match(detail(state,10), /Awaiting score/)
    state.bot.closeWindow(state.bot.currentWindow)
  }
  await command(a, 'score text', /^Current hole strokes: 0$/)
  for (const hole of [1,2]) {
    await until(() => players.every(state => state.rows.get('ndg0')?.includes('Hole '+hole+' / 2')), 'both HUDs advance')
    const finished = new Set()
    while (finished.size < players.length) {
      await until(() => active() && !finished.has(active()) && active().bot.game.gameMode === 'adventure', 'an unfinished player takes the tee')
      const state = active()
      const turn = a.turns.length
      await command(state, 'lie')
      await pause(225)
      let pin
      await until(() => (pin = state.bot.findBlock({matching:state.bot.registry.blocksByName.yellow_concrete.id,maxDistance:65})), 'basket loads')
      const shot = await basketShot(state.bot, 'driver', pin.position.offset(.5,-1.1,.5))
      assert.ok(shot, 'fixture has an open shot to the basket')
      await release(state, shot)
      await until(() => a.messages.some(line => line.startsWith(state.name+' completed hole '+hole+'!')), 'basket counted')
      finished.add(state)
      if (hole === 1 && finished.size === 1) {
        await menu(state, 'score', 'Round Scorecard')
        assert.match(detail(state,9), /1 stroke.*Basket/s)
        assert.equal(detail(state,9).includes('ndg:gui/under_par'), state === a)
        state.bot.closeWindow(state.bot.currentWindow)
      }
      if (hole === 1 || finished.size < players.length) await until(() => a.turns.length > turn, 'the turn advances')
    }
  }

  await until(() => players.every(state => state.messages.includes('[Open Round Summary]')
    && state.bot.game.gameMode === state.original.mode), 'finish and restore the lobby before opening both summaries')
  for (const state of players) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    await menu(state, 'summary', 'Round Summary')
    assert.match(detail(state,4), /2 strokes/)
    assert.match(detail(state,11), /Longest drive/)
    assert.equal(detail(state,12).includes('ndg:gui/putt'), state === a)
    assert.match(detail(state,13), /2\/2 \(100%\)/)
    assert.match(detail(state,14), /no putts/)
    assert.equal(detail(state,15).includes('ndg:gui/penalty'), state === a)
    await command(state, 'summary text', /^OB penalties: 0$/)
  }
  console.log('PASS: textured and vanilla HUDs and scorecards, scored-hole tiles, and private post-round analytics after a complete two-player round.')
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(() => {
  for (const {bot} of players) bot.quit()
})
