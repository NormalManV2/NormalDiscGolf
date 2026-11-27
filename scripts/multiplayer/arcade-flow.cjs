const assert = require('node:assert/strict')
const fs = require('node:fs')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { tee, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const abilities = [
  {id:'arcade-phantom',name:'Phantom Disc',item:'echo_shard',slot:3,effect:'Phantom phased through an obstacle.'},
  {id:'arcade-rocket',name:'Rocket Disc',item:'firework_rocket',slot:4,effect:'Rocket boosted in flight.'},
  {id:'arcade-sticky',name:'Sticky Disc',item:'honeycomb',slot:5,effect:'Sticky stopped on contact.'}
]
const wallFile = process.env.NDG_ARCADE_WALL_REQUEST || 'build/arcade-wall-request.json'
const players = ['A','B'].map(side=>connect('Arcade'+side+process.pid%10000))
const [first,second] = players
const active = () => players.find(state=>state.name===first.turns.at(-1))
const inventoryItems = state => state.bot.inventory.slots.slice(39,42).map(item=>item?.name)
const charges = used => 'Arcade charges • Phantom '+(used>0 ? 0 : 1)
  +' • Rocket '+(used>1 ? 0 : 1)+' • Sticky '+(used>2 ? 0 : 1)

async function status(state, line) {
  const before = state.messages.length
  await command(state,'status')
  await until(()=>state.messages.slice(before).includes(line),line)
  return state.messages.slice(before)
}

async function denied(state, text, complaint) {
  const before = state.messages.length
  state.bot.chat(text)
  await until(()=>state.messages.slice(before).includes(complaint),complaint)
  assert.ok(!state.messages.slice(before).some(line=>line.startsWith('Throw meter started')))
}

async function queue(format, useMenu = false) {
  const turns = first.turns.length
  const message = format==='arcade' ? 'Your party has been queued for Arcade.' : 'Your party has been queued for FFA.'
  if (useMenu) {
    const opened = once(first.bot,'windowOpen')
    await command(first,'menu')
    const [picker] = await opened
    await until(()=>picker.slots[25]?.name==='firework_rocket','Arcade is in the round picker')
    await clickModeJoin(first, 25)
    await until(()=>first.messages.includes(message),'Arcade party joins the queue')
  } else await command(first,'queue '+format,new RegExp('^'+message.replace('.','\\.')+'$'))
  await until(()=>first.turns.length>turns && players.every(state=>state.bot.game.gameMode==='adventure'),
    'the round starts after the flyover',120000)
  await pause(400)
  for (const state of players) {
    await status(state,'Hole: 1/2')
    await command(state,'shot timing on',/timing on/)
  }
}

async function prepareWalls() {
  const lines = await status(first,'Hole: 1/2')
  const world = lines.find(line=>line.startsWith('Round: '))?.slice(7)
  assert.ok(world?.startsWith('ndg_arcade_'))
  assert.ok(active().bot.entity.position.distanceTo(tee)<2)
  const blocks = [[60,67,56],[64,67,56]]
  const commands = blocks.map(position=>'execute in minecraft:'+world+' run setblock '+position.join(' ')+' minecraft:stone')
  fs.writeFileSync(wallFile,JSON.stringify({world,commands},null,2))
  console.log('FIXTURE: '+commands.join(' | '))
  await until(()=>blocks.every(([x,y,z])=>first.bot.blockAt(new Vec3(x,y,z))?.name==='stone'),'console builds the two fixture blocks',90000)
}

async function canceledAim(state) {
  const before = state.messages.length
  state.bot.chat('/throwdisc arcade-phantom')
  await until(()=>state.messages.slice(before).includes('Throw meter started for Phantom Disc (backhand).'),'start a Phantom preview')
  state.bot.setQuickBarSlot(6)
  await pause(150)
  await status(state,charges(0))
  assert.ok(!state.messages.slice(before).some(line=>line.includes('charges remaining:')),'aim cancellation spends no charge')
}

function aimSpecial(state, index) {
  const {bot} = state
  const start = bot.entity.position
  if (index===0) return shortShot(bot,55,0,start.offset(10,0,0))
  if (index===1) return shortShot(bot,35,-.19,start.offset(0,0,10))
  return shortShot(bot,25,-.68,start.offset(0,0,10))
}

async function useAbilities() {
  await canceledAim(active())
  await denied(players.find(state=>state!==active()),'/throwdisc arcade-phantom',"It is not your team's turn.")
  for (const state of players) await status(state,charges(0))
  for (const [index,ability] of abilities.entries()) {
    const used = new Set()
    for (let attempt=0;attempt<2;attempt++) {
      await until(()=>active() && !used.has(active()),'the next player gets the ability turn')
      const state = active()
      used.add(state)
      await command(state,'lie')
      await pause(230)
      const before = state.messages.length, turns = first.turns.length
      await release(state,aimSpecial(state,index),ability.slot)
      const messages = state.messages.slice(before)
      assert.ok(messages.includes(ability.name+' charges remaining: 0'),'the launch spends this charge')
      assert.equal(messages.filter(line=>line===ability.effect).length,1,'the real flight triggered '+ability.name+' once')
      if (index===2) assert.ok(!messages.includes('Ground skip'),'Sticky stops without a skip')
      await status(state,charges(index+1))
      await until(()=>first.turns.length>turns,'the stroke advances the turn')
    }
    console.log('PASS: both players used '+ability.name+' and spent one independent charge')
  }
}

async function rejectSpent(state) {
  for (const ability of abilities)
    await denied(state,'/throwdisc '+ability.id,ability.name+' has no charges left this round.')
  state.bot.setQuickBarSlot(3)
  await pause(120)
  await denied(state,'/throwdisc','Phantom Disc has no charges left this round.')
}

async function finishOpeningHole() {
  const finished = state => first.messages.some(line=>line.startsWith(state.name+' completed hole 1!'))
  for (let attempt=0;attempt<6 && !players.every(finished);attempt++) {
    await until(()=>active() && !finished(active()),'an unfinished player gets the next stroke')
    const state = active()
    await rejectSpent(state)
    await command(state,'lie')
    await pause(240)
    const shot = await basketShot(state.bot) || shortShot(state.bot,30,-.32)
    const turns = first.turns.length
    await release(state,shot,2)
    await until(()=>first.turns.length>turns || players.every(finished),'normal disc finishes the stroke')
  }
  assert.ok(players.every(finished),'both players hole out using their regular discs')
}

async function restore() {
  await until(()=>players.every(state=>state.bot.game.gameMode===state.original.mode),'players return to the lobby')
  await pause(350)
  for (const state of players) {
    assert.ok(state.bot.entity.position.distanceTo(state.original.position)<2)
    assert.deepEqual(inventory(state.bot),state.original.items)
    await status(state,'You are not in a round or queue.')
  }
}

async function main() {
  await until(()=>players.every(state=>state.spawned),'both 1.21.8 clients connect')
  await command(first,'party invite '+second.name,/Party invite sent/)
  await command(second,'party accept',/joined the party/)
  await queue('arcade',true)
  for (const state of players) assert.deepEqual(inventoryItems(state),abilities.map(ability=>ability.item))
  await prepareWalls()
  await useAbilities()
  await finishOpeningHole()
  await until(()=>players.every(state=>state.bot.game.gameMode==='adventure'),'second tee flyover ends')
  await pause(400)
  for (const state of players) {
    await status(state,'Hole: 2/2')
    await status(state,charges(3))
  }
  for (let turn=0;turn<2;turn++) {
    const state = active(), before = first.turns.length
    await rejectSpent(state)
    await command(state,'concede',/^Hole conceded:/)
    await until(()=>first.turns.length>before || first.messages.includes('Round complete!'),'the final hole advances')
  }
  await restore()
  for (const state of players) {
    assert.ok(state.messages.includes('Final standings:'))
    assert.ok(state.messages.includes('Round reward: +10 credits. Balance: 10. Open /ndg shop.'))
    assert.ok(!state.messages.some(line=>line.startsWith('Round rating:')))
  }
  console.log('PASS: charges stayed spent across holes, regular discs scored baskets, and standings and rewards completed')

  await queue('arcade')
  for (const state of players) await status(state,charges(0))
  for (const state of players) await command(state,'quit',/forfeited/)
  await restore()
  console.log('PASS: a new Arcade round starts with fresh charges')

  await queue('ffa')
  await denied(active(),'/throwdisc arcade-phantom','Special discs are available in Arcade rounds.')
  for (const state of players) assert.deepEqual(inventoryItems(state),[undefined,undefined,undefined])
  for (const state of players) await command(state,'quit',/forfeited/)
  await restore()
  console.log('PASS: regular stroke play has no loaners and rejects Arcade disc commands')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async ()=>{
  for (const {bot} of players) bot.quit()
  await pause(700)
  process.exit(process.exitCode||0)
})
