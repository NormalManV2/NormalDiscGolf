const mineflayer = require('mineflayer')
const fs = require('node:fs')
const assert = require('node:assert/strict')
const bots = [], transcript = [], states = new Map()
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
const until = async (predicate, label, timeout = 120000) => {
  const deadline = Date.now() + timeout
  while (!predicate()) {
    if (Date.now() > deadline) throw Error('Timed out: ' + label)
    await pause(50)
  }
}
function connect(name) {
  const bot = mineflayer.createBot({host:'127.0.0.1',port:25569,username:name,version:'1.21.8',auth:'offline',physicsEnabled:false})
  const state = {ready:false,messages:[],active:false,titleResets:0}
  bots.push(bot); states.set(bot,state)
  bot._client.on('clear_titles',()=>{state.titleResets++})
  bot.once('spawn',()=>{
    state.ready = true
    state.original = {mode:bot.game.gameMode,position:bot.entity.position.clone(),items:bot.inventory.items().map(i=>[i.name,i.count]).sort()}
  })
  bot.on('messagestr',(message,position)=>{
    if (position === 'game_info') {state.active = message.startsWith('Your turn'); return}
    state.messages.push(message); transcript.push({player:name,message})
  })
  return bot
}
let passed = false
async function main() {
  connect('NDG_Echo'); connect('NDG_Foxtrot')
  await until(()=>bots.every(b=>states.get(b).ready),'connections')
  bots[0].chat('/ndg party invite NDG_Foxtrot'); await pause(400)
  bots[1].chat('/ndg party accept'); await pause(400)
  bots[0].chat('/ndg queue ffa')
  await until(()=>bots.every(b=>b.game.gameMode==='spectator'),'preview')
  // Exercise the actual 1.21.8 input packet rather than the skip command.
  const skipped = Date.now()
  bots.forEach(b=>b.setControlState('sneak',true))
  await until(()=>bots.every(b=>b.game.gameMode==='adventure'),'sneak skips preview',3000)
  assert.ok(Date.now()-skipped<3000,'preview skipped before its six-second duration')
  await until(()=>bots.every(b=>states.get(b).titleResets>0),'preview titles cleared',1000)
  bots.forEach(b=>b.setControlState('sneak',false))
  await until(()=>bots.some(b=>states.get(b).active),'active thrower')
  const active = bots.find(b=>states.get(b).active), other = bots.find(b=>b!==active)
  other.chat('/throwdisc putter')
  await until(()=>states.get(other).messages.some(m=>m.includes("not your team's turn")),'out-of-turn rejection',10000)
  active.activateItem()
  await until(()=>states.get(active).messages.some(m=>m.includes('Throw meter started')),'meter',10000)
  await pause(500); active.swingArm('right')
  await until(()=>states.get(active).messages.includes('Disc released.'),'release',10000)
  active.quit()
  await until(()=>states.get(other).active,'remaining player takes turn',10000)
  other.chat('/ndg status')
  await until(()=>states.get(other).messages.includes('Players: 1'),'departed player removed',10000)
  other.chat('/ndg lie'); await pause(250)
  other.activateItem()
  await until(()=>states.get(other).messages.some(m=>m.includes('Throw meter started')),'shot after disconnect',10000)
  other.swingArm('right')
  await until(()=>states.get(other).messages.includes('Disc released.'),'release after disconnect',10000)
  await pause(6000)
  other.chat('/ndg quit')
  const original = states.get(other).original
  await until(()=>other.game.gameMode===original.mode,'mode restoration',10000)
  assert.ok(other.entity.position.distanceTo(original.position)<2,'location restored')
  assert.deepEqual(other.inventory.items().map(i=>[i.name,i.count]).sort(),original.items,'inventory restored')
  passed = true
  console.log('PASS: sneak skip, out-of-turn rejection, disconnect during flight, continued play, and quit restoration.')
}
main().catch(error=>{console.error(error.stack);process.exitCode=1}).finally(()=>{
  fs.mkdirSync('build',{recursive:true})
  fs.writeFileSync('build/multiplayer-disconnect.json',JSON.stringify({passed,transcript},null,2))
  bots.forEach(b=>b.quit())
  setTimeout(()=>process.exit(process.exitCode||0),1000)
})
