const assert = require('node:assert/strict')
const { readFileSync, readdirSync, writeFileSync } = require('node:fs')
const { once } = require('node:events')
const { resolve } = require('node:path')
const { basket, basketShot, command, connect, inventory, pause, release, shortShot, until } = require('./parkland-41.cjs')

const report = resolve('course-records-restart.json')
const afterRestart = process.argv.includes('--after-restart')
const previous = afterRestart ? JSON.parse(readFileSync(report, 'utf8')) : null
const players = (previous?.names || ['Record_A', 'Record_B'].map(name => name + process.pid % 10000)).map(connect)
const [owner, friend] = players
const itemText = item => JSON.stringify(item?.components ?? [])
const active = () => players.find(state => state.name === owner.turns.at(-1))
const frames = new Map()
const ghosts = new Map()
let capturing = null

for (const state of players) {
  frames.set(state, [])
  ghosts.set(state, [])
  state.bot._client.on('world_particles', packet => {
    const position = [packet.x, packet.y, packet.z]
    if (packet.particle.type === 'end_rod' && state === capturing) frames.get(state).push(position)
    if (packet.particle.type === 'dust' && (packet.particle.data.color & 0xffffff) === 0xa88bff)
      ghosts.get(state).push(position)
  })
}

async function open(state, action) {
  const opened = once(state.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await action()
  const [window] = await opened
  return window
}

async function party(leader, member) {
  await command(leader, 'party invite ' + member.name, /Party invite sent/)
  await command(member, 'party accept', /joined the party/)
}

async function start(queue, library) {
  const marks = players.map(state => state.messages.length)
  const from = owner.turns.length
  await queue()
  await until(() => owner.turns.length > from && players.every(state => state.bot.game.gameMode === 'adventure'),
    'both players are on the tee', 120000)
  for (const state of players) {
    await command(state, 'status', new RegExp('^Parkland.*Seed 41.*Library Line' + (library ? '.*Course Library$' : '$')))
    await command(state, 'shot timing on', /timing on/)
  }
  return marks
}

async function play(marks, extraThrows = 0, concede = false, capture = false) {
  const complete = new Set()
  while (complete.size < players.length) {
    await until(() => active() && !complete.has(active()), 'an unfinished player gets the turn')
    const state = active()
    const from = owner.turns.length
    const mark = state.messages.length
    await command(state, 'lie')
    await pause(250)
    if (concede && state === friend) {
      await command(state, 'concede', /^Hole conceded:/)
      complete.add(state)
    } else {
      const miss = state === owner && extraThrows > 0
      const shot = miss ? shortShot(state.bot, 20, -1.48, basket) : await basketShot(state.bot, 'driver', basket)
      assert.ok(shot, 'the actual basket has a playable line')
      if (capture) capturing = state
      await release(state, shot)
      capturing = null
      if (miss) {
        extraThrows--
      } else {
        await until(() => state.messages.slice(mark).some(line => line.startsWith(state.name + ' completed hole 1!')),
          state.name + ' holes out')
        complete.add(state)
      }
    }
    await until(() => owner.turns.length > from || complete.size === players.length, 'the turn advances')
  }
  await until(() => players.every((state, index) => state.messages.slice(marks[index]).includes('Round complete!')
    && state.bot.game.gameMode === state.original.mode), 'both finish and return from the round')
  for (const state of players) assert.deepEqual(inventory(state.bot), state.original.items)
}

async function ownedDetails() {
  const menu = await open(owner, () => command(owner, 'library'))
  await until(() => itemText(menu.slots[9]).includes('Right click for course details and records'), 'saved layout appears')
  const preview = await open(owner, () => owner.bot.clickWindow(9, 1, 0))
  await until(() => itemText(preview.slots[29]).includes('Course Records'), 'record board is exposed from a saved course')
  return preview
}

async function listRecords(state, window, expected) {
  assert.match(itemText(window.slots[29]), /Course Records/)
  const board = await open(state, () => state.bot.clickWindow(29, 0, 0))
  await until(() => itemText(board.slots[49]).includes('Your Course Best'), 'graphical course records open')
  for (const line of expected) {
    if (/^\d+\./.test(line)) {
      const [name, score] = line.split(' - ')
      assert.ok(board.slots.slice(9, 45).some(item => itemText(item).includes(name) && itemText(item).includes(score)))
    } else if (line.startsWith('Your course best:')) {
      assert.ok(itemText(board.slots[49]).includes(line.replace('Your course best: ', '')))
    } else if (line.startsWith('Hole out')) {
      assert.match(itemText(board.slots[22]), /No completed cards yet/)
    }
  }
  const back = await open(state, () => state.bot.clickWindow(45, 0, 0))
  await until(() => itemText(back.slots[29]).includes('Course Records'), 'records return to the opening course details')
  const reopened = await open(state, () => state.bot.clickWindow(29, 0, 0))
  await until(() => itemText(reopened.slots[53]).includes('Show in Chat'), 'records offer the text view')
  const mark = state.messages.length
  await state.bot.clickWindow(53, 0, 0)
  await until(() => expected.every(row => state.messages.slice(mark).some(line => line.startsWith(row))), 'expected course standings')
}

async function sharedDetails(code) {
  const menu = await open(friend, () => command(friend, 'library code ' + code))
  await until(() => itemText(menu.slots[29]).includes('Course Records'), 'shared preview has the same record board')
  return menu
}

async function favorites(state, expected) {
  const library = await open(state, () => command(state, 'library'))
  await until(() => itemText(library.slots[51]).includes('Show Favorites'), 'library offers favorites filter')
  await state.bot.clickWindow(51, 0, 0)
  await until(() => itemText(library.slots[51]).includes('Show All Courses'), 'library switches into favorites')
  if (expected) assert.match(itemText(library.slots[9]), /Favorite/)
  else assert.match(itemText(library.slots[22]), /No favorite courses/)
  await state.bot.clickWindow(51, 0, 0)
  await until(() => itemText(library.slots[51]).includes('Show Favorites'), 'library returns to all courses')
  assert.match(itemText(library.slots[9]), /Parkland/)
  state.bot.closeWindow(library)
}

async function changeFavorite(value) {
  const details = await ownedDetails()
  assert.match(itemText(details.slots[26]), new RegExp(value ? 'Add to Favorites' : 'Remove from Favorites'))
  const updated = await open(owner, () => owner.bot.clickWindow(26, 0, 0))
  await until(() => itemText(updated.slots[26]).includes(value ? 'Remove from Favorites' : 'Add to Favorites'),
    'favorite action updates the course details')
}

async function verifyGhost(state, other, strokes, recorded) {
  const score = () => open(state, () => command(state, 'score'))
  const compare = await score()
  await until(() => itemText(compare.slots[53]).includes('Compare Course Ghost'), 'the scorecard offers comparison')
  assert.match(itemText(compare.slots[9]), new RegExp('Ghost target: ' + strokes + ' stroke'))
  const comparisonMark = state.messages.length
  await state.bot.clickWindow(53, 0, 0)
  await until(() => state.messages.slice(comparisonMark).some(line =>
    new RegExp('^Course ghost scorecard .* Best ' + strokes + ' strokes$').test(line)), 'scorecard compares to the correct ghost')
  const observerCount = ghosts.get(other).length
  const overlay = await score()
  await until(() => itemText(overlay.slots[51]).includes('Show Ghost Paths'), 'scorecard offers to enable the path overlay')
  const overlayMark = ghosts.get(state).length
  await state.bot.clickWindow(51, 0, 0)
  await until(() => itemText(overlay.slots[51]).includes('Hide Ghost Paths') && ghosts.get(state).length > overlayMark,
    'scorecard toggles private path particles on')
  await state.bot.clickWindow(51, 0, 0)
  await until(() => itemText(overlay.slots[51]).includes('Show Ghost Paths'), 'scorecard toggles paths off')

  const mark = ghosts.get(state).length
  const mode = state.bot.game.gameMode
  await state.bot.clickWindow(47, 0, 0)
  await until(() => ghosts.get(state).length >= mark + 4, 'successive frames arrive from ghost playback')
  const played = ghosts.get(state).slice(mark)
  const matching = played.filter(point => recorded.some(frame =>
    Math.hypot(...point.map((value, axis) => value - frame[axis])) < .065))
  assert.ok(matching.length >= played.length - 1, 'playback matches this player’s actual recorded throw')
  assert.equal(ghosts.get(other).length, observerCount, 'the other player receives no ghost particles')
  assert.equal(state.bot.game.gameMode, mode, 'ghost replay keeps the player in their playing mode')
  await command(state, 'library ghost off', /^Course ghost paths are off\.$/)
  const stopped = ghosts.get(state).length
  await pause(450)
  assert.equal(ghosts.get(state).length, stopped, 'off cancels the running replay')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients join', 30000)
  await command(owner, 'library ghost play', /^Course ghosts are available while playing a saved-layout stroke round\.$/)
  if (!afterRestart) {
    await party(owner, friend)
    const generated = await start(() => command(owner, 'queue ffa new parkland library-line', /queued for FFA/), false)
    await play(generated)
    const summary = await open(owner, () => command(owner, 'summary'))
    await until(() => itemText(summary.slots[33]).includes('Save Course'), 'round summary offers the layout')
    await owner.bot.clickWindow(33, 0, 0)
    await until(() => itemText(summary.slots[33]).includes('Course Saved'), 'layout is saved')
    await favorites(owner, false)
    await changeFavorite(true)
    await favorites(owner, true)
    const library = resolve('plugins/NormalDiscGolf/course-library')
    const folder = readdirSync(library)[0]
    const id = readdirSync(resolve(library, folder))[0].slice(0, -4)
    const codeMatch = /^Share code for .*: ([A-HJ-NP-Z2-9]{7})$/
    await command(owner, 'library share ' + id, codeMatch)
    const code = owner.messages.find(line => codeMatch.test(line)).match(codeMatch)[1]
    await listRecords(owner, await ownedDetails(), ['Course records •', 'Hole out on every hole in a saved-layout stroke round'])

    const first = await start(() => command(owner, 'library play ' + id, /Your party queued/), true)
    await command(owner, 'library ghost play', /^Hole out on every hole to record your first Course ghost\.$/)
    await play(first, 1, false, true)
    const scores = ['New course best: -1 (2 strokes)', 'New course best: -2 (1 stroke)']
    for (const [index, state] of players.entries()) {
      const messages = state.messages.slice(first[index])
      assert.ok(messages.includes(scores[index]))
      assert.ok(messages.some(line => line.startsWith('Course ghost saved.')))
      assert.ok(frames.get(state).length >= 4, 'actual trajectory samples were captured')
    }
    await listRecords(friend, await sharedDetails(code), ['1. ' + friend.name + ' - -2 (1 stroke)',
      '2. ' + owner.name + ' - -1 (2 strokes)', 'Your course best: -2 (1 stroke)'])
    await sharedDetails(code)
    const copy = await open(friend, () => friend.bot.clickWindow(22, 0, 0))
    await until(() => itemText(copy.slots[22]).includes('Saved to Your Library'), 'friend saves the shared layout')
    await favorites(friend, false)
    writeFileSync(report, JSON.stringify({ names: players.map(state => state.name), id, code,
      frames: players.map(state => frames.get(state)) }, null, 2))
    console.log('PASS: separate course bests, graphical standings from a shared preview, and private favorites.')
    return
  }

  await party(friend, owner)
  await favorites(owner, true)
  await changeFavorite(false)
  await favorites(owner, false)
  await sharedDetails(previous.code)
  const second = await start(() => friend.bot.clickWindow(33, 0, 0), true)
  await verifyGhost(owner, friend, 2, previous.frames[0])
  await verifyGhost(friend, owner, 1, previous.frames[1])
  await play(second, 0, true)
  assert.ok(owner.messages.slice(second[0]).includes('New course best: -2 (1 stroke)'))
  assert.ok(owner.messages.slice(second[0]).some(line => line.startsWith('Course ghost saved.')))
  assert.ok(friend.messages.slice(second[1]).includes('Hole out on every hole to post a course record.'))
  assert.ok(!friend.messages.slice(second[1]).some(line => line.startsWith('Course ghost saved.')))
  await listRecords(owner, await ownedDetails(), ['1. ' + owner.name + ' - -2 (1 stroke)',
    '1. ' + friend.name + ' - -2 (1 stroke)', 'Your course best: -2 (1 stroke)'])
  console.log('PASS: favorites survive restart; scorecards replay private ghosts, and graphical standings preserve ties and ignore concessions.')
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
