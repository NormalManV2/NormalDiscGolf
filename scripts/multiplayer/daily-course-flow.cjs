const assert = require('node:assert/strict')
const fs = require('node:fs')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const reportFile = process.env.NDG_DAILY_TEST_REPORT || 'build/daily-course-restart.json'
const afterRestart = process.argv.includes('--after-restart')
const previous = afterRestart ? JSON.parse(fs.readFileSync(reportFile, 'utf8')) : null
const names = previous?.names || ['A', 'B', 'C'].map(side => 'Daily' + side + process.pid % 10000)
const players = names.map(connect)
const [leader, partner, solo] = players
const active = () => players.find(state => state.name === leader.turns.at(-1))
const description = /^Daily Course (\d{4}-\d{2}-\d{2}) UTC .* Seed (-?\d+) .* 1 hole$/

async function board(state, date = null) {
  const mark = state.messages.length
  await command(state, date ? 'daily leaderboard ' + date : 'daily', /^Daily leaderboard \(/)
  await pause(180)
  const messages = state.messages.slice(mark)
  const header = messages.find(line => description.test(line))
  assert.ok(header, 'Daily leaderboard names its date, seed, and round length')
  const [, day, seed] = header.match(description)
  return { day, seed, header, messages, rows:messages.filter(line => /^\d+\. .+ - [+-]?\d+ \(\d+ strokes?\)$/.test(line)) }
}

async function playFromMenu() {
  let opened = once(leader.bot, 'windowOpen')
  await command(leader, 'menu')
  const [picker] = await opened
  assert.equal(picker.slots[21]?.name, 'clock')
  let mark = leader.messages.length
  await leader.bot.clickWindow(21, 1, 0)
  await until(() => leader.messages.slice(mark).some(line => line.startsWith('Daily leaderboard (')), 'right-click opens the daily leaderboard')
  opened = once(leader.bot, 'windowOpen')
  await command(leader, 'menu')
  const [playPicker] = await opened
  assert.equal(playPicker.slots[21]?.name, 'clock')
  mark = leader.messages.length
  await clickModeJoin(leader, 21)
  await until(() => leader.messages.slice(mark).includes('Your party has been queued for Daily Course.'), 'left-click queues the daily party')
}

async function playRound(course, roundNumber) {
  const turns = leader.turns.length
  const included = roundNumber === 1 ? [leader, partner] : players
  const marks = new Map(included.map(state => [state, state.messages.length]))
  if (roundNumber === 1) await playFromMenu()
  else {
    await command(leader, 'daily play', /Your party has been queued for Daily Course/)
    await command(solo, 'queue daily', /You have been queued for Daily Course/)
  }
  await until(() => leader.turns.length > turns && included.every(({bot}) => bot.game.gameMode === 'adventure'),
    'Daily Course starts with all entrants', 120000)
  let world
  for (const state of included) {
    const mark = state.messages.length
    await command(state, 'status', /^Round: ndg_daily_/)
    const lines = state.messages.slice(mark)
    assert.ok(lines.includes(course.header), 'the daily round matches the advertised seed and date')
    assert.ok(lines.includes('Hole: 1/1') && lines.includes('Course event: Clear'))
    const instance = lines.find(line => line.startsWith('Round: ')).slice(7)
    world ||= instance
    assert.equal(instance, world, 'solo and party queue into the same daily round')
    assert.deepEqual(state.bot.inventory.slots.slice(36, 39).map(item => item?.name), ['slime_ball','heart_of_the_sea','fire_charge'])
    await command(state, 'shot timing on', /timing on/)
  }

  let anchors
  const strokes = new Map(included.map(state => [state, 0]))
  const finished = new Set()
  for (let attempts = 0; attempts < 8 && finished.size < included.length; attempts++) {
    await until(() => active() && !finished.has(active()), 'the next daily player gets a turn')
    const state = active()
    const count = leader.turns.length
    const messages = state.messages.length
    if (state === solo) {
      await command(solo, 'concede', /^Hole conceded:/)
      finished.add(solo)
    } else {
      await command(state, 'lie')
      await pause(250)
      if (!anchors) {
        const tee = state.bot.entity.position.clone()
        const cap = state.bot.registry.blocksByName.yellow_concrete.id
        let pin
        await until(() => (pin = state.bot.findBlock({matching:cap, maxDistance:65})), 'the real basket is visible to the client')
        const target = pin.position.offset(.5, -1.1, .5)
        anchors = [tee.toArray(), target.toArray()]
      }
      const basket = new Vec3(...anchors[1])
      const takeExtraStroke = roundNumber === 1 ? state === leader : state === partner
      const before = strokes.get(state)
      const shot = takeExtraStroke && before === 0 ? shortShot(state.bot, 20, -1.48, basket)
        : await basketShot(state.bot, 'driver', basket)
      assert.ok(shot, 'a sampled flight reaches the real basket from ' + state.bot.entity.position + ' to ' + basket)
      await release(state, shot)
      strokes.set(state, before + 1)
      if (!takeExtraStroke || before > 0) {
        await until(() => state.messages.slice(messages).some(line => line.startsWith(state.name + ' completed hole 1!')), 'real daily basket scores')
        finished.add(state)
      }
    }
    await until(() => leader.turns.length > count || finished.size === included.length, 'the daily turn advances')
  }
  assert.equal(finished.size, included.length)
  await until(() => included.every(state => state.messages.slice(marks.get(state)).includes('Round complete!')), 'everyone receives final results')
  await until(() => included.every(({bot, original}) => bot.game.gameMode === original.mode
    && bot.entity.position.distanceTo(original.position) < 2), 'lobby state is restored')
  for (const state of included) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    const lines = state.messages.slice(marks.get(state))
    assert.ok(!lines.some(line => line.startsWith('Round rating:')))
    assert.ok(lines.some(line => line.startsWith(state === solo ? 'Hole out on every hole to post' : 'Daily best:')))
  }
  return { world, anchors }
}

async function main() {
  await until(() => players.every(state => state.spawned), 'all Minecraft Java 1.21.8 clients join')
  if (afterRestart) {
    const actual = await board(leader, previous.day)
    assert.equal(actual.header, previous.header)
    assert.deepEqual(actual.rows, previous.rows)
    assert.ok(actual.messages.some(line => line.startsWith('Your daily best: ') && line.endsWith('Place 1')))
    console.log('PASS: leaderboard ties, personal bests, and the frozen date, seed, and course settings survived a Paper restart')
    return
  }
  const today = await board(leader)
  assert.equal(today.day, new Date().toISOString().slice(0, 10))
  assert.notEqual(today.seed, '41', 'the Daily Course seed is independent of the general fixed seed')
  await command(leader, 'party invite ' + partner.name, /Party invite sent/)
  await command(partner, 'party accept', /joined the party/)
  const first = await playRound(today, 1)
  const firstBoard = await board(leader)
  assert.ok(firstBoard.rows.includes('1. ' + partner.name + ' - -2 (1 stroke)'))
  assert.ok(firstBoard.rows.some(line => line.endsWith('. ' + leader.name + ' - -1 (2 strokes)')))
  console.log('PASS: two players finished the Daily Course and the leaderboard ranked the lower score first')

  const second = await playRound(today, 2)
  assert.notEqual(first.world, second.world, 'separately generated round instances')
  assert.deepEqual(first.anchors, second.anchors, 'both copies have the same playable tee and basket coordinates')
  const standings = await board(leader)
  for (const state of [leader, partner]) {
    const row = standings.rows.filter(line => line.includes('. ' + state.name + ' - '))
    assert.equal(row.length,1,'each player has a single best entry')
    assert.equal(row[0], '1. ' + state.name + ' - -2 (1 stroke)', 'equal best scores share first')
  }
  const soloBoard = await board(solo)
  assert.deepEqual(soloBoard.rows, standings.rows)
  assert.ok(!soloBoard.messages.some(line => line.startsWith('Your daily best: ')), 'conceded round never appears on the board')
  fs.writeFileSync(reportFile, JSON.stringify({ names, day:today.day, header:today.header, rows:standings.rows }, null, 2))
  console.log('PASS: separate worlds have the same anchors, better scores replace old bests, worse scores are ignored, and concessions do not post')
  console.log(standings.header + ' | ' + standings.rows.join(' | '))
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  for (const {bot} of players) bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
