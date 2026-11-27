const assert = require('node:assert/strict')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { tee, basket, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const nextTee = new Vec3(152.5,66,72.5)
const nextBasket = new Vec3(216.5,66.9,72.5)
const fairway = new Vec3(177.5,65,72.5)
const roundStart = 'Elimination: The highest stroke count goes out after each hole.'
const forfeited = 'You forfeited the round.'
const clients = (id, count) => Array.from({length:count},(_,i)=>connect('El'+id+'ABC'[i]+process.pid%10000))
const active = (observer, players) => players.find(state=>state.name===observer.turns.at(-1))
const ends = state => state.messages.includes('Round complete!')

async function start(players, checkMinimum) {
  const [first, ...others] = players
  await until(()=>players.every(state=>state.spawned),'clients join the lobby')
  if (checkMinimum) {
    const opened = once(first.bot,'windowOpen')
    await command(first,'menu')
    const [menu] = await opened
    await until(()=>menu.slots[22]?.name==='blaze_powder','Elimination round picker button')
    await clickModeJoin(first, 22)
  } else {
    await command(first,'queue elimination',/You have been queued for Elimination/)
  }
  await until(()=>first.messages.includes('You joined a Elimination round.'),'first player joins the queue')
  if (checkMinimum) {
    await pause(4500)
    await command(first,'status',/^You are queued for Elimination\.$/)
    assert.ok(!first.messages.includes(roundStart),'a solo player remains queued')
    assert.equal(first.bot.game.gameMode,first.original.mode)
    console.log('PASS: the menu queues Elimination and one player waits for opponents')
  }
  await Promise.all(others.map(state=>command(state,'queue elimination',/You have been queued for Elimination/)))
  await until(()=>players.every(state=>state.messages.includes(roundStart)),'Elimination round starts',120000)
  for (const state of players) await command(state,'shot timing on',/timing on/)
  await ready(first,players,1)
}

async function ready(observer, players, hole) {
  const start = hole===1 ? tee : nextTee
  await until(()=>players.every(state=>state.bot.game.gameMode==='adventure') &&
    active(observer,players)?.bot.entity.position.distanceTo(start)<2,'players return to tee '+hole)
  await command(observer,'status',new RegExp('^Hole: '+hole+'/2$'))
  await pause(300)
}

async function stage(observer, players, extras, hole, done) {
  const target = hole===1 ? basket : nextBasket
  const shots = new Map()
  for (let turn=0; turn<30 && !done(); turn++) {
    const state = active(observer,players)
    assert.ok(state,'only players in this stage can take a turn')
    const count = shots.get(state) || 0
    await command(state,'lie')
    await pause(240)
    let shot
    if (count<(extras.get(state)||0)) {
      shot = shortShot(state.bot,20,-.45,target)
    } else if (state.bot.entity.position.distanceTo(target)<=56) {
      shot = await basketShot(state.bot,'driver',target)
    }
    shot ||= await basketShot(state.bot,'driver',fairway)
    assert.ok(shot,state.name+' has a shot line on hole '+hole)
    const turns = observer.turns.length
    await release(state,shot)
    shots.set(state,count+1)
    await until(()=>done() || observer.turns.length>turns,'stage advances')
  }
  assert.ok(done(),'stage reaches its expected outcome')
}

function restored(state) {
  const {bot,original} = state
  assert.equal(bot.game.gameMode,original.mode,'game mode restored')
  assert.ok(bot.entity.position.distanceTo(original.position)<2,'lobby location restored')
  assert.deepEqual(inventory(bot),original.items,'inventory restored')
}

async function playThree(players) {
  const [first, second, third] = players
  await start(players,true)
  const firstTie = () => first.messages.some(line=>line.startsWith('Hole 1 tied at 2 strokes. Shootout 1:'))
  await stage(first,players,new Map([[second,1],[third,1]]),1,firstTie)
  const tie = first.messages.find(line=>line.startsWith('Hole 1 tied at'))
  assert.ok(tie.includes(second.name) && tie.includes(third.name) && !tie.includes(first.name))
  await command(first,'score',new RegExp('^'+first.name+': safe$'))
  const before = first.messages.length
  first.bot.chat('/throwdisc')
  await until(()=>first.messages.slice(before).includes('You are safe this hole. Wait for the shootout to finish.'),'safe players cannot throw')
  const knockout = third.name+' was eliminated on hole 1 (2 strokes).'
  const shootoutStart = first.messages.length
  await stage(first,[second,third],new Map([[third,1]]),1,()=>first.messages.includes(knockout))
  const shootoutLines = first.messages.slice(shootoutStart)
  assert.ok(shootoutLines.some(line=>line.startsWith(second.name+' completed hole 1!')))
  assert.ok(shootoutLines.some(line=>line.startsWith(third.name+' completed hole 1!')))
  assert.ok(!shootoutLines.some(line=>line.startsWith(first.name+' completed hole 1!')))
  await pause(400)
  restored(third)
  await command(third,'status',/^You are not in a round or queue\.$/)
  console.log('PASS: the tie replay is restricted to the two highest scorers, and third place returns to the lobby')

  await ready(first,[first,second],2)
  await command(first,'status',/^Players remaining: 2$/)
  await stage(first,[first,second],new Map([[second,3]]),2,()=>ends(first))
  await until(()=>players.every(ends),'the result reaches all original entrants')
  await pause(500)
  for (const state of players) {
    assert.ok(state.messages.includes('Elimination winner: '+first.name+'!'),'last survivor wins')
    assert.ok(state.messages.includes('1. '+first.name))
    assert.ok(state.messages.includes('2. '+second.name))
    assert.ok(state.messages.includes('3. '+third.name))
    assert.ok(state.messages.some(line=>line.startsWith('Round reward: +')),'basket credits paid to every finisher')
    restored(state)
  }
  for (const state of [first,second])
    assert.ok(first.messages.some(line=>line.startsWith(state.name+' completed hole 2!')),'both finalists finished with real baskets')
  console.log('PASS: both finalists hole out, the higher scorer is eliminated, and all entrants receive their placements and credits')
}

async function repeatedTie(players) {
  const [first] = players
  await start(players,false)
  for (let replay=0; replay<=2; replay++) {
    const opening = active(first,players)
    const other = players.find(state=>state!==opening)
    await command(opening,'concede',/^Hole conceded:/)
    await until(()=>active(first,players)===other,'other side takes the turn')
    await command(other,'concede',/^Hole conceded:/)
    if (replay<2) {
      await until(()=>first.messages.some(line=>line.includes('Shootout '+(replay+1)+':')),'both tied players replay')
      await pause(250)
    }
  }
  await until(()=>players.every(ends),'draw resolves the repeated tie')
  assert.equal(first.messages.filter(line=>line.includes('was eliminated on hole 1 (draw among players tied')).length,1)
  assert.equal(first.messages.filter(line=>line.startsWith('Elimination winner: ')).length,1)
  await pause(400)
  players.forEach(restored)
  console.log('PASS: two tied shootouts trigger one draw and the last player wins')
}

async function quit(players) {
  const [first, second] = players
  await start(players,false)
  await command(first,'quit',new RegExp('^'+first.name+' forfeited Elimination\\.$'))
  await until(()=>first.messages.includes(forfeited) && ends(second),'forfeit finishes the round')
  assert.ok(second.messages.includes('Elimination winner: '+second.name+'!'))
  await pause(400)
  players.forEach(restored)
  console.log('PASS: forfeiting gives the remaining player the win and restores both players')
}

async function run(id, count, test) {
  const players = clients(id,count)
  try { await test(players) }
  finally {
    for (const state of players) state.bot.quit()
    await pause(650)
  }
}

async function main() {
  await run('Flow',3,playThree)
  await run('Tie',2,repeatedTie)
  await run('Quit',2,quit)
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>process.exit(process.exitCode||0))
