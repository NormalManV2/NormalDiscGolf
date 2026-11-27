const assert = require('node:assert/strict')
const { once } = require('node:events')
const { tee, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const suffix = process.pid % 10000
const players = [
  Object.assign(connect('NDGOneA'+suffix),{disc:'driver', label:'Starter Driver', item:'fire_charge', choice:11}),
  Object.assign(connect('NDGOneB'+suffix),{disc:'midrange', label:'Starter Midrange', item:'heart_of_the_sea', choice:10})
]
const observer = players[0]
const active = () => players.find(state=>state.name===observer.turns.at(-1))
const discSlots = bot => bot.inventory.slots.slice(36,42).map(item=>item?.name || null)
const emptySlots = Array(6).fill(null)

async function rejected(state, text, complaint) {
  const before = state.messages.length
  state.bot.chat(text)
  await until(()=>state.messages.slice(before).includes(complaint),text+' is rejected')
  assert.ok(!state.messages.slice(before).some(line=>line.startsWith('Throw meter started')),'the rejected choice starts no meter')
}

async function bag(state, fromHotbar = false) {
  const opened = once(state.bot,'windowOpen')
  if (fromHotbar) {
    state.bot.setQuickBarSlot(7)
    await pause(150)
    state.bot.activateItem()
  } else await command(state,'bag')
  const [window] = await opened
  assert.match(JSON.stringify(window.title),/Round Disc Bag/)
  await until(()=>window.slots[9] && window.slots[10] && window.slots[11],'round bag contains the packed discs')
  return window
}

async function choose(state, fromHotbar) {
  const bot = state.bot
  assert.deepEqual(discSlots(bot),emptySlots,'discs stay in the bag until the player locks one')
  const window = await bag(state,fromHotbar)
  const before = state.messages.length
  await bot.clickWindow(state.choice,0,0)
  await until(()=>state.messages.slice(before).includes('Locked '+state.label+' for this One Disc Challenge.'),'lock message')
  await until(()=>!bot.currentWindow && discSlots(bot)[0]===state.item,'the chosen disc is equipped')
  assert.deepEqual(discSlots(bot),[state.item,null,null,null,null,null])
  assert.equal(window.slots[state.choice].name,state.item,'the equip uses the actual bag selection')

  const reopened = await bag(state)
  const mismatch = state.messages.length
  await bot.clickWindow(9,0,0)
  await until(()=>state.messages.slice(mismatch).includes('Your disc is locked to '+state.label+' for this round.'),'bag rejects a different disc')
  assert.equal(bot.currentWindow,reopened,'the rejected click keeps the bag open')
  bot.closeWindow(reopened)
  await command(state,'shot timing on',/timing on/)
}

async function readyHole(hole) {
  await until(()=>players.every(state=>state.bot.game.gameMode==='adventure') && active(),'players return from the flyover')
  await pause(400)
  for (const state of players) {
    const before = state.messages.length
    await command(state,'status',new RegExp('^Hole: '+hole+'/3$'))
    if (hole>1) {
      assert.ok(state.messages.slice(before).includes('Locked disc: '+state.label),'the same choice survives the hole change')
      assert.deepEqual(discSlots(state.bot),[state.item,null,null,null,null,null])
    }
  }
}

async function finishHole(hole) {
  const shots = new Map(players.map(state=>[state,0]))
  const done = () => hole===1
    ? players.every(state=>observer.messages.some(line=>line.startsWith(state.name+' completed hole 1!')))
    : observer.messages.filter(line=>line==='Hole conceded: hole recorded at the stroke limit.').length >= (hole-1)*2
  for (let turn=0; !done() && turn<10; turn++) {
    const state = active()
    const before = observer.turns.length
    const count = shots.get(state)
    if (hole>1 && count>0) {
      await command(state,'concede')
    } else {
      await command(state,'lie')
      await pause(230)
      if (count===0) {
        const locked = 'Your disc is locked to '+state.label+' for this round.'
        await rejected(state,'/throwdisc starter-putter',locked)
        await rejected(state,'/throwdisc putter',locked)
      }
      const shot = hole===1
        ? (await basketShot(state.bot,state.disc) || shortShot(state.bot,65,.02))
        : shortShot(state.bot,30)
      const fromCommand = hole===2
      const begin = state.messages.length
      await release(state,shot,fromCommand ? 6 : 0,fromCommand ? '/throwdisc' : null)
      assert.ok(state.messages.slice(begin).includes('Throw meter started for '+state.label+' (backhand).'),
        'the active throw uses the locked disc')
      shots.set(state,count+1)
    }
    await until(()=>observer.turns.length>before || done(),'the next player or hole is reached')
  }
  assert.ok(done(),'hole '+hole+' completes')
  assert.ok(players.every(state=>shots.get(state)>0),'both players threw from their own locked discs')
}

async function main() {
  await until(()=>players.every(state=>state.spawned),'two clients connect')
  await command(observer,'party invite '+players[1].name,/Party invite sent/)
  await command(players[1],'party accept',/joined the party/)
  const opened = once(observer.bot,'windowOpen')
  await command(observer,'menu')
  const [menu] = await opened
  await until(()=>menu.slots[12]?.name==='slime_ball','One Disc Challenge in the round picker')
  await clickModeJoin(observer, 12)
  await until(()=>observer.messages.includes('Your party has been queued for One Disc Challenge.'),'challenge queues')
  await until(()=>players.every(state=>state.messages.some(line=>line.startsWith('One Disc Challenge.'))),'round begins',120000)
  await readyHole(1)
  assert.ok(active().bot.entity.position.distanceTo(tee)<2,'seed 41 fixture')
  await rejected(active(),'/throwdisc starter-driver','Choose your disc in /ndg bag before throwing.')
  await choose(players[0],true)
  await choose(players[1],false)
  console.log('PASS: separate bag choices, a single hotbar disc each, and lock enforcement')
  for (let hole=1;hole<=3;hole++) {
    if (hole>1) await readyHole(hole)
    await finishHole(hole)
    console.log('PASS: hole '+hole+' used the locked discs'+(hole===1 ? ' for two real baskets' : ''))
  }
  await until(()=>players.every(state=>state.messages.includes('Round complete!')),'round completes for both players')
  await pause(600)
  for (const {bot,messages,original} of players) {
    assert.ok(messages.includes('Final standings:'))
    assert.ok(messages.some(line=>line.startsWith('Round reward: +')),'basket credits paid')
    assert.equal(bot.game.gameMode,original.mode,'game mode restored')
    assert.ok(bot.entity.position.distanceTo(original.position)<2,'lobby location restored')
    assert.deepEqual(inventory(bot),original.items,'inventory restored')
  }
  const restoredBag = once(observer.bot,'windowOpen')
  await command(observer,'bag')
  const [lobbyBag] = await restoredBag
  assert.match(JSON.stringify(lobbyBag.title),/Your Disc Bag/)
  await until(()=>lobbyBag.slots[0]?.name==='slime_ball' && lobbyBag.slots[1]?.name==='heart_of_the_sea'
    && lobbyBag.slots[2]?.name==='fire_charge','lobby bag still contains all three packed discs')
  console.log('PASS: three-hole One Disc Challenge completed with two clients, standings, rewards, and restoration')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async ()=>{
  for (const state of players) state.bot.quit()
  await pause(750)
  process.exit(process.exitCode||0)
})
