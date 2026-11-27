const assert = require('node:assert/strict')
const fs = require('node:fs')
const { once } = require('node:events')
const { until, pause, connect, inventory, command, basketShot, shortShot, release } = require('./parkland-41.cjs')

const reportFile = process.env.NDG_WEEKLY_TEST_REPORT || 'build/weekly-tournament-restart.json'
const afterRestart = process.argv.includes('--after-restart')
const before = afterRestart ? JSON.parse(fs.readFileSync(reportFile, 'utf8')) : null
const names = before?.names || ['A','B','C','D'].map(side=>'Weekly'+side+process.pid%10000)
const players = names.slice(0, afterRestart ? 3 : 4).map(connect)
const [winner, runnerUp, conceding, absent] = players
const playing = players.slice(0, 3)
const active = () => playing.find(state=>state.name===winner.turns.at(-1))

async function balance(state) {
  const mark = state.messages.length
  await command(state, 'career', /^Disc shop balance: \d+ credits$/)
  const line = state.messages.slice(mark).find(line=>line.startsWith('Disc shop balance: '))
  return Number(line.match(/\d+/)[0])
}

async function standings(state, day) {
  const mark = state.messages.length
  await command(state, 'tournament standings ' + day, /^Weekly tournament standings -/)
  await pause(230)
  return state.messages.slice(mark)
}

async function enroll(state) {
  await command(state, 'tournament signup', /^Signed up for the weekly tournament at /)
}

async function scheduledStart(start) {
  const marks = new Map(playing.map(state=>[state,state.messages.length]))
  const time = Date.parse(start)
  assert.ok(Date.now() < time, 'registration finished before the scheduled start')
  assert.ok(playing.every(state=>state.bot.game.gameMode===state.original.mode), 'sign-up leaves players in the lobby')
  await until(()=>playing.every(state=>state.messages.slice(marks.get(state)).includes('The weekly tournament is preparing.')),
    'the clock starts the tournament without a player command', Math.max(10000,time-Date.now()+20000))
  assert.ok(Date.now()>=time, 'preparation starts at or after the advertised time')
  await until(()=>playing.every(state=>state.bot.game.gameMode==='adventure'
    && state.messages.some(line=>line.startsWith('Weekly tournament is live.'))) && active(),
    'registered tournament players start their round',120000)
  for (const state of playing) {
    await command(state,'status',/^Round: ndg_weekly_/)
    assert.ok(state.messages.includes('Hole: 1/1'))
    assert.deepEqual(state.bot.inventory.slots.slice(36,39).map(item=>item?.name),['slime_ball','heart_of_the_sea','fire_charge'])
    await command(state,'shot timing on',/timing on/)
  }
}

async function play() {
  const attempts = new Map()
  const strokes = new Map()
  const finished = new Set()
  let target
  while (finished.size < playing.length) {
    await until(()=>active()&&!finished.has(active()),'an unfinished entrant gets the turn')
    const state = active(), turns = winner.turns.length, mark = state.messages.length
    if (state === conceding) {
      await command(state,'concede',/^Hole conceded:/)
      finished.add(state)
    } else {
      await command(state,'lie')
      await pause(250)
      if (!target) {
        const material = state.bot.registry.blocksByName.yellow_concrete.id
        let cap
        await until(()=>(cap=state.bot.findBlock({matching:material,maxDistance:65})),'the real tournament basket is visible')
        target = cap.position.offset(.5,-1.1,.5)
      }
      const count = attempts.get(state)||0
      const extra = state===runnerUp && (!finished.has(winner) || (strokes.get(state)||0)<strokes.get(winner))
      const shot = extra ? shortShot(state.bot,20,-1.48,target)
        : await basketShot(state.bot,'driver',target) || shortShot(state.bot,30,-.32,target)
      await release(state,shot)
      await until(()=>winner.turns.length>turns||winner.messages.includes('Round complete!'),'the scoring decision arrives')
      attempts.set(state,count+1)
      const messages = state.messages.slice(mark)
      const penalty = messages.includes('Hazard or out of bounds. One penalty stroke. Replay the previous lie.')
      strokes.set(state,(strokes.get(state)||0)+1+(penalty?1:0))
      if (messages.some(line=>line.startsWith(state.name+' completed hole 1!'))) {
        finished.add(state)
      } else {
        assert.ok(count<8,'the scripted player reaches a basket before the stroke limit')
      }
    }
    await until(()=>winner.turns.length>turns||finished.size===playing.length,'the next turn or final results arrive')
  }
  return strokes
}

async function main() {
  await until(()=>players.every(state=>state.spawned),'the Java 1.21.8 clients connect')
  if (afterRestart) {
    const rows = await standings(winner,before.day)
    assert.deepEqual(rows,before.rows)
    for (const [index,state] of playing.entries()) {
      assert.equal(await balance(state),before.balances[index],'retrying payout leaves the recorded balance unchanged')
      assert.ok(!state.messages.some(line=>line.startsWith('Weekly tournament reward: ')))
    }
    console.log('PASS: archived standings survived restart and retrying a payout did not duplicate credits')
    return
  }
  const start = process.env.NDG_WEEKLY_START
  assert.ok(start && Date.parse(start)>Date.now(),'set NDG_WEEKLY_START to the scheduled UTC start')
  const day = start.slice(0,10)
  const opened = once(winner.bot,'windowOpen')
  await command(winner,'menu')
  const [picker] = await opened
  assert.equal(picker.slots[26]?.name,'golden_helmet')
  await winner.bot.clickWindow(26,0,0)
  await until(()=>winner.messages.includes('Weekly tournament starts: '+start),'the picker shows the scheduled event')
  await command(winner,'queue weekly',/^Use \/ndg tournament signup/)
  const balances = []
  for (const state of playing) balances.push(await balance(state))
  for (const state of players) await enroll(state)
  await command(winner,'tournament signup',/^You are already signed up/)
  await command(conceding,'tournament withdraw',/^Withdrew from the weekly tournament/)
  await command(winner,'tournament',/^Registration is open .* 3\/4 signed up/)
  await enroll(conceding)
  await command(winner,'tournament',/^Registration is open .* 4\/4 signed up/)
  assert.ok((await standings(winner,day)).some(line=>line.startsWith('This event starts ')))
  absent.bot.quit()
  console.log('PASS: the schedule, enrollment, duplicate check, withdrawal, and re-enrollment work with ordinary players')

  await scheduledStart(start)
  const strokes = await play()
  await until(()=>playing.every(state=>state.messages.includes('Weekly tournament final standings are available at /ndg tournament standings.')),
    'the event archives its final scores')
  await until(()=>playing.every(({bot,original})=>bot.game.gameMode===original.mode
    && bot.entity.position.distanceTo(original.position)<2),'round cleanup restores the entrants')
  const rows = await standings(winner,day)
  assert.ok(strokes.get(winner)>0 && strokes.get(winner)<strokes.get(runnerUp))
  const row = (place,state,prize) => {
    const score = strokes.get(state)-3
    const total = strokes.get(state)
    return place+'. '+state.name+' - '+(score>0?'+':'')+score+' ('+total+(total===1?' stroke)':' strokes)')+' • '+prize+' prize credits'
  }
  assert.ok(rows.includes(row(1,winner,100)))
  assert.ok(rows.includes(row(2,runnerUp,60)))
  assert.ok(rows.includes(conceding.name+' - Did not finish'))
  assert.ok(rows.includes(absent.name+' - Did not start'))
  assert.ok(!rows.includes('Prize delivery is pending.'))
  const awards = [100,60,0]
  const finalBalances = []
  for (const [index,state] of playing.entries()) {
    assert.deepEqual(inventory(state.bot),state.original.items)
    assert.equal(state.messages.filter(line=>line.startsWith('Weekly tournament reward: ')).length,awards[index]>0?1:0)
    assert.ok(!state.messages.some(line=>line.startsWith('Round rating: ')))
    const total = await balance(state)
    assert.equal(total,balances[index]+awards[index])
    finalBalances.push(total)
  }
  await command(winner,'tournament',/^Weekly tournament starts:/)
  assert.ok(winner.messages.includes('Weekly tournament starts: '+new Date(Date.parse(start)+7*86400000).toISOString().replace('.000Z','Z')))
  fs.writeFileSync(reportFile,JSON.stringify({ names, day, start, rows, balances: finalBalances },null,2))
  console.log('PASS: the scheduled round finished with two real scorecards, one concession and one no-show')
  console.log(rows.join(' | '))
  console.log('PASS: only the finishers received 100 and 60 credits, and the next event is one week later')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async()=>{
  for (const {bot} of players) bot.quit()
  await pause(650)
  process.exit(process.exitCode||0)
})
