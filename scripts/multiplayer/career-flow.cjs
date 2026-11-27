const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { once } = require('node:events')
const { createHash } = require('node:crypto')
const { until, pause, connect, command, basketShot, shortShot, release } = require('./parkland-41.cjs')

const report = process.env.NDG_CAREER_REPORT || 'build/career-profile-reconnect.json'
const readXp = (lines, skill) => lines.find(line=>line.startsWith('- '+skill+': '))
const linePattern = /^(Division:|Rating:|Rated rounds:|Peak rating:|Last round rating:|Next division:|Average score|Disc shop balance:|- )/

function offlineUuid(name) {
  const id = createHash('md5').update('OfflinePlayer:'+name).digest()
  id[6] = (id[6] & 15) | 0x30
  id[8] = (id[8] & 63) | 0x80
  const hex = id.toString('hex')
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-')
}

function prepareProfiles(names) {
  const directory = process.env.NDG_CAREER_PROFILE_DIR
  assert.ok(directory,'set NDG_CAREER_PROFILE_DIR to the profile directory of the loopback test server')
  fs.mkdirSync(directory,{recursive:true})
  const fixtures = [
    'schema-version: 5\nskills:\n  backhand: {level: 1, xp: 55}\n',
    'schema-version: 5\nrating:\n  rating: 950\n  division: JUNIOR\n  total-rounds: 2\n  total-score: -6\n  average-score: -3\nskills:\n  backhand: {level: 1, xp: 50}\n  power: {level: 5, xp: 0}\n'
  ]
  names.forEach((name,index)=>fs.writeFileSync(path.join(directory,offlineUuid(name)+'.yml'),fixtures[index],{flag:'wx'}))
}

async function profile(state) {
  const start = state.messages.length
  await command(state,'career',/^- Thumber:/)
  const lines = state.messages.slice(start)
  assert.ok(lines.includes('Disc golf career'))
  return lines.filter(line=>linePattern.test(line))
}

async function openRound(players, format) {
  const [first,second] = players
  const start = first.messages.length, turns = first.turns.length
  await command(first,'queue '+format,/Your party has been queued/)
  await until(()=>first.turns.length>turns && players.every(state=>state.bot.game.gameMode==='adventure'),
    'a new round starts after the flyover',120000)
  await pause(450)
  await command(first,'status',/^Hole: 1\/1$/)
  for (const state of players) await command(state,'shot timing on',/timing on/)
  if (format==='ffa')
    assert.ok(first.messages.slice(start).includes('Career XP is active. Every player uses the same skill bonuses in this rated format.'))
  return players.map(state=>state.messages.length)
}

async function playRated(players, offsets) {
  const [first,second] = players
  const active = () => players.find(state=>state.name===first.turns.at(-1))
  const finished = state => first.messages.slice(offsets[0]).some(line=>line.startsWith(state.name+' completed hole 1!'))
  const throws = new Map(players.map(state=>[state,0]))
  while (!players.every(finished)) {
    await until(()=>active() && !finished(active()),'an unfinished player receives the turn')
    const state = active(), previous = throws.get(state)
    const before = first.turns.length
    await command(state,'lie')
    await pause(240)
    const aim = state===second && previous<3 ? shortShot(state.bot,20,-1.48) : await basketShot(state.bot)
    assert.ok(aim,'seed 41 has a basket line for '+state.name+' from '+state.bot.entity.position)
    await release(state,aim)
    throws.set(state,previous+1)
    await until(()=>players.every(finished) || first.turns.length>before,'the next turn or completed round arrives')
    assert.ok(throws.get(state)<6,'the scripted scorecard holes out')
  }
  assert.equal(throws.get(first),1)
  assert.equal(throws.get(second),4)
}

async function playPractice(players, offsets, concede) {
  const [first] = players
  const completed = () => first.messages.slice(offsets[0]).includes('Round complete!')
  const used = new Set()
  for (let step=0;step<2;step++) {
    const active = () => players.find(state=>state.name===first.turns.at(-1) && !used.has(state))
    await until(active,'player receives an attempt')
    const state = active(), before = first.turns.length
    used.add(state)
    await command(state,'lie')
    await pause(220)
    await release(state,shortShot(state.bot,20,-.6))
    await until(()=>completed() || first.turns.length>before,'practice throw resolves')
  }
  if (concede) {
    for (let step=0;step<2;step++) {
      const state = players.find(p=>p.name===first.turns.at(-1)), before = first.turns.length
      await command(state,'concede',/^Hole conceded:/)
      await until(()=>completed() || first.turns.length>before,'conceded scorecard advances')
    }
  }
  await until(completed,'practice round completes')
  for (const [i,state] of players.entries()) {
    assert.ok(!state.messages.slice(offsets[i]).some(line=>line.startsWith('Round rating:')),'this scorecard earned no rating')
    if (concede)
      assert.ok(state.messages.slice(offsets[i]).includes('To earn a rating, throw in the round and hole out on every hole.'))
  }
}

async function verifyRestart() {
  const expected = JSON.parse(fs.readFileSync(report,'utf8'))
  const players = expected.map(entry=>connect(entry.name))
  try {
    await until(()=>players.every(state=>state.spawned),'the same profiles load after a server restart')
    for (const [i,state] of players.entries())
      assert.deepEqual(await profile(state),expected[i].lines,'rating, division, peak, rounds and all visible XP persisted')
    console.log('PASS: both career profiles match exactly after the server restart and reconnect')
  } finally {
    for (const {bot} of players) bot.quit()
    await pause(700)
  }
}

async function main() {
  if (process.argv.includes('--verify-restart')) return verifyRestart()
  const names = ['A','B'].map(side=>'Career'+side+process.pid%100000)
  prepareProfiles(names)
  const players = names.map(connect)
  const [first,second] = players
  try {
    await until(()=>players.every(state=>state.spawned),'the two seed profiles join')
    const initialA = await profile(first), initialB = await profile(second)
    assert.ok(initialA.includes('Rating: Unrated'))
    assert.ok(initialB.includes('Division: MPO — Professional'))
    assert.match(readXp(initialA,'Backhand'),/1\/5 \(55/)
    assert.match(readXp(initialB,'Driving'),/5\/5 \(max\)/)
    const opened = once(first.bot,'windowOpen')
    await command(first,'clubhouse')
    const [clubhouse] = await opened
    await until(()=>clubhouse.slots[31]?.name==='experience_bottle','the Clubhouse shows the Career shortcut')
    const shortcut = first.messages.length
    await first.bot.clickWindow(31,0,0)
    await until(()=>first.messages.slice(shortcut).includes('Disc golf career'),'the Clubhouse opens the career profile')
    await command(first,'party invite '+second.name,/Party invite sent/)
    await command(second,'party accept',/joined the party/)

    const rated = await openRound(players,'ffa')
    await playRated(players,rated)
    await until(()=>players.every((state,i)=>state.messages.slice(rated[i]).includes('Round complete!')),'rated round completes')
    assert.ok(first.messages.includes('Round rating: 1116 • Career rating: Unrated → 824'))
    assert.ok(first.messages.includes('Promoted to MA3 — Recreational.'))
    assert.ok(second.messages.includes('Round rating: 792 • Career rating: 950 → 927'))
    assert.ok(second.messages.includes('Division changed to MA1 — Advanced.'))
    for (const state of players)
      assert.ok(state.messages.some(line=>line.startsWith('Skill XP:')) && state.messages.includes('Level up! backhand is now level 2.'))
    const cleanA = await profile(first), cleanB = await profile(second)
    assert.match(readXp(cleanA,'Backhand'),/2\/5 \(0/)
    assert.match(readXp(cleanB,'Backhand'),/2\/5 \(10/)
    console.log('PASS: legal throws awarded XP and level-ups, and clean FFA scorecards moved both career ratings and divisions')

    const incomplete = await openRound(players,'ffa')
    await playPractice(players,incomplete,true)
    const arcade = await openRound(players,'closest-to-pin')
    await playPractice(players,arcade,false)
    const persisted = []
    for (const [i,state] of players.entries()) {
      const lines = await profile(state)
      assert.ok(lines.includes(i===0 ? 'Rating: 824' : 'Rating: 927'))
      assert.ok(lines.includes(i===0 ? 'Rated rounds: 1' : 'Rated rounds: 3'))
      assert.match(readXp(lines,'Backhand'),i===0 ? /2\/5 \(10/ : /2\/5 \(20/)
      persisted.push({name:state.name,lines})
    }
    fs.writeFileSync(report,JSON.stringify(persisted,null,2))
    console.log('PASS: conceded and arcade rounds awarded skill XP and left both career ratings intact')
  } finally {
    for (const {bot} of players) bot.quit()
    await pause(700)
  }
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>process.exit(process.exitCode||0))
