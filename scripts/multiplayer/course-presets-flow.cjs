const assert = require('node:assert/strict')
const { once } = require('node:events')
const { command, connect, inventory, openCourseLobbies, pause, release, shortShot, until } = require('./parkland-41.cjs')

const itemText = item => JSON.stringify(item?.components ?? [])
const find = (window, text, start = 0, end = 54) =>
  window.slots.findIndex((item, slot) => slot >= start && slot < end && itemText(item).includes(text))

async function browse(state, modeSlot = 13) {
  const opened = once(state.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await command(state, 'menu')
  const [picker] = await opened
  await until(() => picker.slots[modeSlot]?.name, 'mode tile is available')
  return openCourseLobbies(state, modeSlot)
}

async function click(state, window, text, start, end) {
  await until(() => find(window, text, start, end) >= 0, text+' appears')
  await state.bot.clickWindow(find(window, text, start, end), 0, 0)
}

async function browseRidge(state, modeSlot) {
  const room = await browse(state, modeSlot)
  await click(state, room, 'New Mountain Lobby', 36, 45)
  await until(() => itemText(room.slots[4]).includes('Mountain Presets'), 'Mountain preset page')
  assert.equal(find(room, 'Server Default', 9, 45), -1, 'the Woodland default is unavailable on Mountain')
  assert.equal(find(room, 'Staff Ridge', 9, 45), -1, 'a private preset stays hidden')
  assert.equal(find(room, 'Sky Trial', 9, 45), -1, 'the other terrain’s preset stays hidden')
  return room
}

async function choose(state, name, length, seed, width) {
  const room = await browseRidge(state)
  await until(() => find(room, name, 9, 45) >= 0, name+' is selectable')
  const entry = itemText(room.slots[find(room, name, 9, 45)])
  assert.match(entry, new RegExp(length+' holes?.*Seed '+seed), 'preset lists its own seed and length')
  assert.match(entry, new RegExp('Fairway '+width+' blocks'), 'preset lists its own width')
  await click(state, room, name, 9, 45)
  await until(() => state.messages.includes('You have been queued for Match Play.'), name+' queues its creator')
}

async function main() {
  const clients = ['Preset_Alice','Preset_Bob','Preset_Casey'].map(connect)
  const [alice,bob,casey] = clients
  try {
    await until(() => clients.every(state => state.spawned), 'all Java 1.21.8 clients join', 30000)
    await pause(600)
    const original = clients.map(({bot}) => ({mode:bot.game.gameMode, position:bot.entity.position.clone(), items:inventory(bot)}))
    await command(casey, 'queue match new mountain staff-ridge', /course preset is unavailable/)
    await command(casey, 'queue match new mountain unknown', /course preset is unavailable/)
    await command(casey, 'queue match new mountain sky-trial', /terrain is unavailable/)
    await command(casey, 'queue daily new mountain fast-ridge', /course preset is unavailable/)
    await command(casey, 'status', /not in a round or queue/)

    const short = await browseRidge(casey, 23)
    await until(() => find(short, 'Fast Ridge', 9, 45) >= 0, 'short-mode preset')
    assert.match(itemText(short.slots[find(short, 'Fast Ridge', 9, 45)]), /2 holes/, 'Ace Race shows the mode length')
    await click(casey, short, 'Back to lobbies', 45, 46)
    await until(() => itemText(short.slots[48]).includes('Quick Join'), 'Back returns to live lobbies')
    casey.bot.closeWindow(short)

    const list = await browse(casey)
    await choose(alice, 'Fast Ridge', 1, 7001, 22)
    await choose(bob, 'Long Ridge', 2, 8102, 12)
    await until(() => find(list, 'Preset: Fast Ridge', 0, 36) >= 0 && find(list, 'Preset: Long Ridge', 0, 36) >= 0,
      'two named lobbies appear without reopening the browser')
    const fastRoom = itemText(list.slots[find(list, 'Preset: Fast Ridge', 0, 36)])
    const longRoom = itemText(list.slots[find(list, 'Preset: Long Ridge', 0, 36)])
    assert.match(fastRoom, /1 hole.*Seed 7001.*Preset_Alice/)
    assert.match(longRoom, /2 holes.*Seed 8102.*Preset_Bob/)
    await click(casey, list, 'Preset: Fast Ridge', 0, 36)

    const playing = [alice,casey]
    await until(() => playing.every(state => state.bot.game.gameMode === 'adventure' && state.turns.length),
      'selected two-player match starts', 120000)
    assert.equal(bob.bot.game.gameMode, original[1].mode, 'the other preset lobby keeps waiting')
    const active = () => playing.find(player => player.name === alice.turns.at(-1))
    for (const state of playing) {
      await command(state, 'status', /^Layout: Glacier Pass$/)
      assert.ok(state.messages.includes('Hole: 1/1'))
      assert.ok(state.messages.some(line => /^Mountain.*Seed 7001.*Preset Fast Ridge$/.test(line)))
    }
    const opener = active()
    assert.ok(Math.abs(opener.bot.entity.position.y - 105) < 1, 'the flat Mountain override is present in the generated world')
    await command(opener, 'shot timing on', /timing on/)
    await release(opener, shortShot(opener.bot, 20, -.35, opener.bot.entity.position.offset(10, 0, 0)), 0, '/throwdisc putter')
    await pause(500)
    await command(active(), 'concede', /Hole 1: .*wins \(Hole conceded\)/)
    await until(() => playing.every(state => state.messages.includes('Round complete!')), 'the selected round finishes')
    await pause(500)
    for (const state of playing) {
      const before = original[clients.indexOf(state)]
      assert.equal(state.bot.game.gameMode, before.mode)
      assert.ok(state.bot.entity.position.distanceTo(before.position) < 2, 'returned to the lobby')
      assert.deepEqual(inventory(state.bot), before.items)
    }

    await command(bob, 'leave', /left the disc golf queue/)
    await command(bob, 'queue match new', /You have been queued for Match Play/)
    const defaults = await browse(casey)
    await until(() => find(defaults, 'Woodland Lobby', 0, 36) >= 0, 'server default still creates a Woodland lobby')
    const defaultRoom = itemText(defaults.slots[find(defaults, 'Woodland Lobby', 0, 36)])
    assert.match(defaultRoom, /4 holes.*Seed 41/)
    assert.doesNotMatch(defaultRoom, /Preset: /)
    console.log('PASS: terrain preset selection, restrictions, separate named lobbies, preset geometry, Match Play completion, and server defaults.')
  } finally {
    for (const state of clients) state.bot.quit()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
