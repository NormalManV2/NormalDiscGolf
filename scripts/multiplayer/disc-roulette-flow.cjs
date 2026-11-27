const assert = require('node:assert/strict')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { tee, basket, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const discs = [
  {type:'putter', label:'Starter Putter', item:'slime_ball'},
  {type:'midrange', label:'Starter Midrange', item:'heart_of_the_sea'},
  {type:'driver', label:'Starter Driver', item:'fire_charge'}
]
const holes = [
  {tee, basket, route: []},
  {tee: new Vec3(152.5,66,72.5), basket: new Vec3(216.5,66.9,72.5),
    route: [168,184,200].map(x=>new Vec3(x+.5,66.5,72.5))},
  {tee: new Vec3(344.5,66,40.5), basket: new Vec3(248.5,66.9,104.5),
    route: [[328,56],[312,56],[296,72],[280,88],[264,88]].map(([x,z])=>new Vec3(x+.5,66.5,z+.5))}
]
const flightRange = {driver: 60, midrange: 45, putter: 37}
const suffix = process.pid % 10000
const players = [connect('NDGRoulA'+suffix),connect('NDGRoulB'+suffix)]
const [observer, partner] = players
const active = () => players.find(state=>state.name===observer.turns.at(-1))
const draws = state => state.messages.filter(line=>line.startsWith('Disc Roulette drew '))
const assigned = state => discs.find(model=>draws(state).at(-1)==='Disc Roulette drew '+model.label+'.')
const hotbar = bot => bot.inventory.slots.slice(36,42).map(item=>item?.name || null)

async function openBag(state, title) {
  const opened = once(state.bot,'windowOpen')
  await command(state,'bag')
  const [window] = await opened
  assert.match(JSON.stringify(window.title),title)
  return window
}

async function packDistinctBags() {
  for (const [state,remove] of [[observer,0],[partner,1]]) {
    const window = await openBag(state,/Your Disc Bag/)
    await until(()=>window.slots[remove]?.name===discs[remove].item,'starter bag contents')
    await state.bot.clickWindow(remove,0,0)
    state.packed = discs.filter((_,i)=>i!==remove)
    await until(()=>state.packed.every((disc,i)=>window.slots[i]?.name===disc.item),'distinct bags packed')
    state.bot.closeWindow(window)
  }
}

async function reject(state, text, reply) {
  const before = state.messages.length
  state.bot.chat(text)
  await until(()=>state.messages.slice(before).includes(reply),text+' is rejected')
  assert.ok(!state.messages.slice(before).some(line=>line.startsWith('Throw meter started')))
}

async function verifyDraw(state, disc) {
  const count = draws(state).length
  const other = state.packed.find(model=>model!==disc)
  const message = 'Disc Roulette assigned '+disc.label+' for this throw.'
  await command(state,'status',new RegExp('^Drawn disc: '+disc.label+'$'))
  assert.ok(state.messages.includes('Mode: Disc Roulette'))
  assert.deepEqual(hotbar(state.bot),[disc.item,null,null,null,null,null])
  const window = await openBag(state,/Round Disc Bag/)
  await until(()=>window.slots[4]?.name==='amethyst_shard' && window.slots[10], 'draw and eligible discs shown in the round bag')
  assert.deepEqual(window.slots.slice(9,11).map(item=>item.name),state.packed.map(model=>model.item))
  const beforeClick = state.messages.length
  await state.bot.clickWindow(9+state.packed.indexOf(other),0,0)
  await until(()=>state.messages.slice(beforeClick).includes(message),'bag does not change the draw')
  assert.equal(state.bot.currentWindow,window)
  state.bot.closeWindow(window)
  await reject(state,'/throwdisc starter-'+other.type,message)
  await reject(state,'/throwdisc '+other.type,message)
  await command(state,'lie')
  await pause(180)
  const beforeAim = state.messages.length
  state.bot.chat('/throwdisc')
  await until(()=>state.messages.slice(beforeAim).includes('Throw meter started for '+disc.label+' (backhand).'),'aim with the drawn disc')
  state.bot.setQuickBarSlot(6)
  await pause(200)
  assert.equal(draws(state).length,count,'canceling aim keeps the draw')
  await command(state,'status',new RegExp('^Drawn disc: '+disc.label+'$'))
}

async function ready(hole) {
  await until(()=>players.every(state=>state.bot.game.gameMode==='adventure') && active(),'hole flyovers end')
  await pause(400)
  await command(observer,'status',new RegExp('^Hole: '+hole+'/3$'))
  assert.ok(active().bot.entity.position.distanceTo(holes[hole-1].tee)<2,'seed 41 fixture')
}

async function aim(bot, disc, hole, attempts) {
  const {basket: target, route} = holes[hole-1]
  if (hole===1 && attempts===0) return shortShot(bot,20,-.45,target)
  const pos = bot.entity.position
  const remaining = pos.distanceTo(target)
  if (remaining<=flightRange[disc.type]) {
    const shot = await basketShot(bot,disc.type,target)
    if (shot) return shot
  }
  const lanes = route.filter(point=>point.distanceTo(target)<remaining-8
      && pos.distanceTo(point)>=10 && pos.distanceTo(point)<=Math.min(28,flightRange[disc.type]))
  const waypoint = lanes.at(-1) || target
  if (waypoint!==target) {
    const shot = await basketShot(bot,disc.type,waypoint)
    if (shot) return shot
  }
  return shortShot(bot,70,attempts%3===0 ? .20 : .08,waypoint.offset(0,0,attempts%2===0 ? 3 : -3))
}

async function playHole(hole) {
  const throws = new Map(players.map(state=>[state,0]))
  const scored = name => observer.messages.some(line=>line.startsWith(name+' completed hole '+hole+'!'))
  for (let turn=0; turn<22 && !players.every(state=>scored(state.name)); turn++) {
    const state = active()
    assert.ok(!scored(state.name),'a completed player cannot throw again on this hole')
    // The observer's turn announcement and the thrower's draw arrive on different connections.
    await until(()=>draws(state).length>state.expectedDraws, state.name+' receives the new draw')
    const disc = assigned(state)
    assert.ok(state.packed.includes(disc),'the draw came from the active player’s packed bag')
    assert.equal(draws(state).length,state.expectedDraws+1,'exactly one new draw per throw')
    await until(()=>hotbar(state.bot)[0]===disc.item, 'the new disc reaches the thrower’s hotbar')
    assert.deepEqual(hotbar(state.bot),[disc.item,null,null,null,null,null])
    const waiting = players.find(player=>player!==state)
    await until(()=>hotbar(waiting.bot).every(item=>item===null), 'the waiting player’s hotbar clears')
    assert.deepEqual(hotbar(waiting.bot),Array(6).fill(null),'waiting players have no assigned disc')
    const attempts = throws.get(state)
    if (hole===1 && attempts===0) {
      await reject(waiting,'/throwdisc',"It is not your team's turn.")
      await verifyDraw(state,disc)
    }
    await command(state,'lie')
    await pause(200)
    const pos = state.bot.entity.position.clone()
    const shot = await aim(state.bot,disc,hole,attempts)
    const before = state.messages.length
    const turnCount = observer.turns.length
    await release(state,shot,attempts%2===0 ? 0 : 6,attempts%2===0 ? null : '/throwdisc')
    assert.ok(state.messages.slice(before).includes('Throw meter started for '+disc.label+' (backhand).'))
    console.log(hole,state.name,disc.label,pos.toString(),scored(state.name) ? 'in' : 'landed')
    state.expectedDraws++
    throws.set(state,attempts+1)
    await until(()=>observer.turns.length>turnCount || observer.messages.includes('Round complete!'),'the next turn begins or the round ends')
  }
  assert.ok(players.every(state=>scored(state.name)),'both players hole out on hole '+hole)
  console.log('PASS: Hole '+hole+' completed by both players with a fresh bag draw before every throw')
}

async function main() {
  await until(()=>players.every(state=>state.spawned),'two Minecraft 1.21.8 clients connect')
  await packDistinctBags()
  await command(observer,'party invite '+partner.name,/Party invite sent/)
  await command(partner,'party accept',/joined the party/)
  const opened = once(observer.bot,'windowOpen')
  await command(observer,'menu')
  const [menu] = await opened
  await until(()=>menu.slots[14]?.name==='amethyst_shard','Disc Roulette is in the round picker')
  await clickModeJoin(observer, 14)
  await until(()=>observer.messages.includes('Your party has been queued for Disc Roulette.'),'Disc Roulette party queues')
  await until(()=>players.every(state=>state.messages.some(line=>line.startsWith('Disc Roulette.'))),'the round begins',120000)
  for (const state of players) {
    state.expectedDraws = 0
    await command(state,'shot timing on',/timing on/)
  }
  for (let hole=1;hole<=3;hole++) {
    await ready(hole)
    await playHole(hole)
  }
  await until(()=>players.every(state=>state.messages.includes('Round complete!')),'round completes for both players')
  await pause(600)
  for (const state of players) {
    const {bot,messages,original} = state
    assert.equal(draws(state).length,state.expectedDraws,'no extra draws after round completion')
    assert.ok(messages.includes('Final standings:'))
    assert.ok(messages.some(line=>line.startsWith('Round reward: +')),'basket credits paid')
    assert.ok(messages.some(line=>line.endsWith(' on a 3-hole Disc Roulette card.')),'the mode records its own clean scorecard')
    assert.equal(bot.game.gameMode,original.mode)
    assert.ok(bot.entity.position.distanceTo(original.position)<2)
    assert.deepEqual(inventory(bot),original.items)
    const lobbyBag = await openBag(state,/Your Disc Bag/)
    await until(()=>state.packed.every((disc,i)=>lobbyBag.slots[i]?.name===disc.item),'packed bag is restored')
    bot.closeWindow(lobbyBag)
  }
  console.log('PASS: round completion, standings, credits, inventories, game modes, locations, and both packed bags')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async ()=>{
  for (const state of players) state.bot.quit()
  await pause(750)
  process.exit(process.exitCode||0)
})
