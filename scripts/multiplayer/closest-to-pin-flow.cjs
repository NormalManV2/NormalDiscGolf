const assert = require('node:assert/strict')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

// Three short holes generated with course seed 41.
const fixture = [
  [new Vec3(72.5,66,88.5),new Vec3(88.5,66.9,72.5)],
  [new Vec3(152.5,66,56.5),new Vec3(200.5,66.9,56.5)],
  [new Vec3(280.5,66,56.5),new Vec3(312.5,66.9,72.5)]
]
const opening = 'Closest to Pin gives you one throw per hole.'
const completed = state => state.messages.includes('Round complete!')
const active = players => players.find(state=>state.name===players[0].turns.at(-1))
const distance = tenths => (tenths/10).toFixed(1)+' blocks'
const faults = state => state.attempts.filter(value=>value===null).length
const total = state => state.attempts.reduce((sum,attempt)=>sum+(attempt ?? 0),0)
const label = state => distance(total(state))+(faults(state) ? ' ('+faults(state)+(faults(state)===1 ? ' fault)' : ' faults)') : '')

async function start(players, party) {
  await until(()=>players.every(state=>state.spawned),'clients join the lobby')
  const [first, ...others] = players
  if (party) {
    for (const member of others) {
      await command(first,'party invite '+member.name,/Party invite sent/)
      await command(member,'party accept',/joined the party/)
    }
    const opened = once(first.bot,'windowOpen')
    await command(first,'menu')
    const [menu] = await opened
    await until(()=>menu.slots[24]?.name==='ender_eye','Closest to Pin is in the round picker')
    await clickModeJoin(first, 24)
    await until(()=>first.messages.includes('Your party has been queued for Closest to Pin.'),'the party joins the queue')
  } else {
    await Promise.all(players.map(state=>command(state,'queue closest-to-pin',/You have been queued for Closest to Pin/)))
  }
  await until(()=>players.every(state=>state.messages.includes(opening)),'the round starts',120000)
  for (const state of players) await command(state,'shot timing on',/timing on/)
}

async function rejectThrow(state, expected) {
  const before = state.messages.length
  state.bot.chat('/throwdisc driver')
  await until(()=>state.messages.slice(before).includes(expected),'extra or out-of-turn attempt rejected')
  assert.ok(!state.messages.slice(before).some(line=>line.startsWith('Throw meter started')))
}

async function hole(players, number, shots) {
  const [observer] = players
  const [tee,basket] = fixture[number-1]
  await until(()=>players.every(state=>state.bot.game.gameMode==='adventure')
      && active(players)?.bot.entity.position.distanceTo(tee)<2,'players return from the flyover')
  await command(observer,'status',new RegExp('^Hole: '+number+'/3$'))
  if (number===1) await rejectThrow(players.find(state=>state!==active(players)),"It is not your team's turn.")

  const used = new Set()
  for (let i=0; i<players.length; i++) {
    const state = active(players)
    assert.ok(state && !used.has(state),'each player gets exactly one turn on a hole')
    used.add(state)
    const shot = shots[players.indexOf(state)]
    const before = observer.messages.length
    const turns = observer.turns.length
    if (shot==='pass') {
      const result = state.name+' got no score on hole '+number+' (Hole conceded).'
      await command(state,'concede',/got no score .*\(Hole conceded\)/)
      await until(()=>observer.messages.slice(before).includes(result),result)
      state.attempts.push(null)
    } else {
      await command(state,'lie')
      await pause(200)
      assert.ok(state.bot.entity.position.distanceTo(tee)<2)
      const aim = shot==='basket' ? await basketShot(state.bot,'driver',basket) : shortShot(state.bot,shot,-.6,basket)
      assert.ok(aim,'the fixture offers a basket line')
      await release(state,aim)
      const prefix = state.name+' finished hole '+number+' at '
      await until(()=>observer.messages.slice(before).some(line=>line.startsWith(prefix)),'the landing distance is announced')
      const result = observer.messages.slice(before).find(line=>line.startsWith(prefix))
      const measured = Math.round(parseFloat(result.slice(prefix.length))*10)
      assert.ok(Number.isFinite(measured))
      if (shot==='basket') assert.equal(measured,0,'a basket is zero distance')
      else {
        const teeDistance = Math.round(Math.hypot(tee.x-basket.x,tee.z-basket.z)*10)
        assert.ok(measured>teeDistance-70 && measured<teeDistance-4,'a deliberately short throw scores where it stops')
      }
      state.attempts.push(measured)
    }
    await until(()=>observer.turns.length>turns || completed(observer),'the next turn or result is reached')
    if (i<players.length-1) {
      await rejectThrow(state,'Your Closest to Pin attempt is complete. Wait for the next hole.')
      const attempt = state.attempts.at(-1)
      const expected = 'Total: '+label(state)+' • This hole: '+(attempt===null ? 'No score' : distance(attempt))
      const start = state.messages.length
      await command(state,'status')
      await until(()=>state.messages.slice(start).includes(expected),'live status shows the attempt and cumulative score')
    }
  }
  console.log('PASS: hole '+number+' scored '+players.map(state=>state.attempts.at(-1)===null ? 'fault' : distance(state.attempts.at(-1))).join(', '))
}

async function finish(players, result, standings, basketCounts) {
  await until(()=>players.every(completed),'every player sees the final result')
  await pause(450)
  for (const [i,state] of players.entries()) {
    assert.ok(state.messages.includes(result),result)
    const index = state.messages.lastIndexOf('Final standings:')
    assert.deepEqual(state.messages.slice(index+1,index+1+standings.length),standings)
    const rewards = state.messages.filter(line=>line.startsWith('Round reward: +'))
    assert.equal(rewards.length,basketCounts[i]>0 ? 1 : 0,'only made baskets pay credits')
    if (basketCounts[i]>0) assert.ok(rewards[0].startsWith('Round reward: +'+basketCounts[i]*10+' credits.'))
    assert.equal(state.bot.game.gameMode,state.original.mode)
    assert.ok(state.bot.entity.position.distanceTo(state.original.position)<2)
    assert.deepEqual(inventory(state.bot),state.original.items)
    await command(state,'status',/^You are not in a round or queue\.$/)
  }
}

async function run(name, count, play) {
  const players = Array.from({length:count},(_,i)=>Object.assign(connect('Pin'+name+'ABC'[i]+process.pid%10000),{attempts:[]}))
  try { await play(players) }
  finally {
    for (const {bot} of players) bot.quit()
    await pause(700)
  }
}

async function main() {
  await run('Tie',3,async players=>{
    const [a,b,c] = players
    await start(players,true)
    await hole(players,1,[20,20,15])
    assert.equal(a.attempts[0],b.attempts[0],'identical short throws tie at the scoring precision')
    assert.ok(a.attempts[0]<c.attempts[0],'a closer legal landing scores better than a farther legal landing')
    await hole(players,2,['basket','basket',20])
    await command(c,'score text',/^Closest to Pin scorecard$/)
    await hole(players,3,['basket','basket','basket'])
    await finish(players,a.name+', '+b.name+' tie for first in Closest to Pin with '+label(a)+' each.',
      ['1. '+a.name+' - '+label(a),'1. '+b.name+' - '+label(b),'3. '+c.name+' - '+label(c)],[2,2,1])
    console.log('PASS: three players complete a full round with shared first place on equal, nonzero distances')
  })
  await run('Win',2,async players=>{
    const [a,b] = players
    await start(players,false)
    await hole(players,1,[20,'basket'])
    await hole(players,2,[20,'pass'])
    await hole(players,3,[20,'basket'])
    assert.equal(faults(a),0)
    assert.equal(faults(b),1)
    assert.ok(total(a)>total(b),'the player who passed has a shorter recorded distance')
    await finish(players,a.name+' wins Closest to Pin with '+label(a)+'.',
      ['1. '+a.name+' - '+label(a),'2. '+b.name+' - '+label(b)],[0,2])
    console.log('PASS: an outright winner, a fault outranking distance in the scoring rules, basket payouts, and lobby restoration')
  })
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>process.exit(process.exitCode||0))
