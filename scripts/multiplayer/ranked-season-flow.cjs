const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { createHash } = require('node:crypto')
const { until, pause, connect, command, basketShot, shortShot, release } = require('./parkland-41.cjs')

const reportFile = process.env.NDG_SEASON_REPORT || 'build/ranked-season-restart.json'
const profileDirectory = process.env.NDG_SEASON_PROFILE_DIR
const prize = [300, 180]
const clients = []

function join(name) {
  const state = connect(name)
  clients.push(state)
  return state
}

function profileFile(name) {
  const id = createHash('md5').update('OfflinePlayer:' + name).digest()
  id[6] = (id[6] & 15) | 0x30
  id[8] = (id[8] & 63) | 0x80
  const hex = id.toString('hex')
  return path.join(profileDirectory, hex.replace(/^(........)(....)(....)(....)(............)$/, '$1-$2-$3-$4-$5') + '.yml')
}

async function lines(state, text, response) {
  const mark = state.messages.length
  await command(state, text, response)
  await pause(220)
  return state.messages.slice(mark)
}

async function profile(state) {
  const result = await lines(state, 'career', /^- Thumber:/)
  return {
    rating: result.find(line=>line.startsWith('Rating: ')),
    rounds: result.find(line=>line.startsWith('Rated rounds: ')),
    balance: Number(result.find(line=>line.startsWith('Disc shop balance: ')).match(/\d+/)[0])
  }
}

async function board(state, number) {
  const text = number ? 'season leaderboard ' + number : 'season'
  const result = await lines(state, text, /^Ranked Season \d+ leaderboard$/)
  return {
    number: Number(result.find(line=>/^Ranked Season /.test(line)).match(/\d+/)[0]),
    lines: result,
    rows: result.filter(line=>/^\d+\. /.test(line))
  }
}

async function party([first, second]) {
  await command(first, 'party invite ' + second.name, /Party invite sent/)
  await command(second, 'party accept', /joined the party/)
}

async function play(players, conceded = false) {
  const [first, second] = players
  const marks = players.map(state=>state.messages.length)
  const messages = state=>state.messages.slice(marks[players.indexOf(state)])
  const turn = first.turns.length
  await command(first, 'queue ffa', /Your party has been queued/)
  await until(()=>first.turns.length>turn && players.every(state=>state.bot.game.gameMode==='adventure'),
    'FFA starts after the preview', 90000)
  await command(first, 'status', /^Hole: 1\/1$/)
  for (const state of players) await command(state, 'shot timing on', /timing on/)
  const finished = new Set()
  const attempts = new Map()
  const active = ()=>players.find(state=>state.name===first.turns.at(-1))
  while (finished.size<players.length) {
    await until(()=>active() && !finished.has(active()), 'an unfinished player has the turn')
    const state = active()
    const priorTurn = first.turns.length
    if (conceded) {
      await command(state, 'concede', /^Hole conceded:/)
      finished.add(state)
    } else {
      const attempted = attempts.get(state) || 0
      assert.ok(attempted<8, state.name + ' reaches the basket before the stroke limit')
      await command(state, 'lie')
      await pause(240)
      const shot = state===second && attempted<3 ? shortShot(state.bot, 20, -1.48)
        : await basketShot(state.bot) || shortShot(state.bot, 30, -.32)
      await release(state, shot)
      attempts.set(state, attempted+1)
    }
    await until(()=>first.turns.length>priorTurn || messages(first).includes('Round complete!'),
      'the next turn or final score follows the shot')
    if (messages(state).some(line=>line.startsWith(state.name + ' completed hole 1!'))) finished.add(state)
  }
  await until(()=>players.every(state=>messages(state).includes('Round complete!')
    && state.bot.game.gameMode===state.original.mode), 'both complete and return to the lobby')
  const roundRatings = players.map(state=>messages(state).find(line=>/^Round rating: /.test(line)))
  if (conceded) {
    assert.deepEqual(roundRatings, [undefined,undefined])
    assert.ok(players.every(state=>!messages(state).some(line=>/^Season \d+ rating: /.test(line))))
    return
  }
  return roundRatings.map(line=>Number(line.match(/^Round rating: (\d+)/)[1]))
}

function row(place, name, rating, rounds, credits) {
  return place + '. ' + name + ' - ' + rating + ' rating (' + rounds + ' rated rounds) | ' + credits + ' prize credits'
}

async function verifyRestart() {
  const expected = JSON.parse(fs.readFileSync(reportFile, 'utf8'))
  const players = expected.names.map(join)
  await until(()=>players.every(state=>state.spawned), 'the same client profiles reconnect')
  assert.deepEqual(await board(players[0]), expected.current)
  assert.deepEqual(await board(players[0], 1), expected.archived)
  for (const [index,state] of players.entries()) {
    assert.deepEqual(await profile(state), expected.profiles[index])
    assert.ok(!state.messages.some(line=>/^Season \d+ reward: /.test(line)))
  }
  console.log('PASS: both season boards and profiles survived Paper restart with no duplicate prize credits')
}

async function main() {
  if (process.argv.includes('--after-restart')) return verifyRestart()
  assert.ok(profileDirectory, 'set NDG_SEASON_PROFILE_DIR to the isolated server profile folder')
  const end = process.env.NDG_SEASON_END
  assert.ok(end && Date.parse(end)>Date.now(), 'set NDG_SEASON_END to the fixture season end')
  const names = ['SeasonA','SeasonB'].map(prefix=>prefix+process.pid%100000)
  fs.writeFileSync(profileFile(names[1]), 'schema-version: 7\ntournament-rewards: [weekly-123]\n', {flag:'wx'})
  const first = join(names[0])
  let second = join(names[1])
  await until(()=>first.spawned && second.spawned, 'both Java 1.21.8 clients join')
  const empty = await board(first)
  assert.equal(empty.number, 1)
  assert.ok(empty.lines.includes('Ends ' + end + ' UTC.'))
  assert.ok(empty.lines.includes('Minimum rated rounds to place: 2.'))
  assert.deepEqual(empty.rows, [])
  await party([first, second])
  const initial = await play([first, second])
  const provisional = await board(first)
  assert.deepEqual(provisional.rows, [])
  assert.ok(provisional.lines.includes('Your season rating: ' + initial[0] + ' (1 rated round, 1 rated round left to qualify).'))
  const next = await play([first, second])
  const averages = initial.map((rating,index)=>Math.round((rating+next[index])/2))
  assert.ok(averages[0]>averages[1])
  const rows = names.map((name,index)=>row(index+1, name, averages[index], 2, prize[index]))
  assert.deepEqual((await board(first)).rows, rows)
  await play([first, second], true)
  assert.deepEqual((await board(first)).rows, rows)
  console.log('PASS: two rated multiplayer rounds qualified both players in rating order, and a conceded round left standings intact')

  const before = [await profile(first), await profile(second)]
  assert.ok(Date.now()<Date.parse(end), 'both players have finished before the scheduled end')
  await command(second, 'party leave', /^You left the party\.$/)
  const left = new Promise(resolve=>second.bot.once('end',resolve))
  second.bot.quit()
  await left
  await until(()=>first.messages.includes('Ranked Season 2 is live. See /ndg season.'),
    'season rollover is triggered by the clock', Math.max(15000, Date.parse(end)-Date.now()+15000))
  assert.ok(Date.now()>=Date.parse(end))
  await until(()=>first.messages.includes('Season 1 reward: +300 credits for place 1.'), 'online prize lands')
  const current = await board(first)
  assert.equal(current.number, 2)
  assert.deepEqual(current.rows, [])
  assert.ok(!current.lines.some(line=>line.startsWith('Your season rating: ')))
  const history = await lines(first, 'season history', /^Ranked season history$/)
  assert.ok(history.includes('Season 1 closed ' + end + ' | /ndg season leaderboard 1'))
  const archived = await board(first, 1)
  assert.deepEqual(archived.rows, rows)
  assert.ok(!archived.lines.includes('Prize delivery is pending.'))
  const offlineProfile = fs.readFileSync(profileFile(second.name), 'utf8')
  assert.match(offlineProfile, new RegExp('credits: '+(before[1].balance+prize[1])+'\\b'))
  assert.ok(offlineProfile.includes('season-1') && offlineProfile.includes('weekly-123'))
  second = join(names[1])
  await until(()=>second.spawned, 'runner-up reconnects after offline payout')
  for (const [index,state] of [first,second].entries()) {
    const expected = {...before[index],balance:before[index].balance+prize[index]}
    assert.deepEqual(await profile(state),expected)
  }
  console.log('PASS: automatic rollover opened an empty board, archived standings, and paid 300 online and 180 offline')
  console.log(rows.join(' | '))

  await party([first, second])
  const opening = await play([first, second])
  const nextBoard = await board(first)
  assert.equal(nextBoard.number,2)
  assert.deepEqual(nextBoard.rows,[])
  assert.ok(nextBoard.lines.includes('Your season rating: ' + opening[0] + ' (1 rated round, 1 rated round left to qualify).'))
  assert.deepEqual(await board(first, 1), archived)
  const profiles = [await profile(first), await profile(second)]
  fs.writeFileSync(reportFile, JSON.stringify({names,current:nextBoard,archived,profiles},null,2))
  console.log('PASS: the next rated round starts new progress and leaves the prior season frozen')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async()=>{
  for (const {bot} of clients) bot.quit()
  await pause(650)
  process.exit(process.exitCode||0)
})
