const mineflayer = require('mineflayer')
const fs = require('node:fs')
const assert = require('node:assert/strict')
const { Vec3 } = require('vec3')
const format = process.argv[2] || 'ffa'
const expectedHoles = Number(process.argv[3] || 18)
const names = format === 'doubles' ? ['NDG_Alice','NDG_Bob','NDG_Cara','NDG_Drew'] : ['NDG_Alice','NDG_Bob']
const bots = [], transcript = [], states = new Map(), results = []
const pause = ms => new Promise(r => setTimeout(r,ms))
const until = async (predicate, label, ms=90000) => {
  const deadline=Date.now()+ms
  while(!predicate()) { if(Date.now()>deadline) throw Error('Timed out: '+label); await pause(100) }
}
const command = (bot,text) => bot.chat('/ndg '+text)
let finished = false
function connect(name) {
  const bot=mineflayer.createBot({host:'127.0.0.1',port:25569,username:name,version:'1.21.8',auth:'offline',physicsEnabled:false})
  const state={messages:[],ready:false,active:false,hole:0,shots:new Map(),completed:false,lock:false,previewFrames:0,maxPreviewPitch:0}
  states.set(bot,state);bots.push(bot)
  bot.on('error',error=>{state.error=error.message})
  bot.on('kicked',reason=>{state.error=JSON.stringify(reason)})
  bot.on('move',()=>{
    if(bot.game.gameMode==='spectator') {
      state.previewFrames++
      state.maxPreviewPitch=Math.max(state.maxPreviewPitch,Math.abs(bot.entity.pitch)*180/Math.PI)
    }
  })
  bot.once('spawn',()=>{state.ready=true;state.original={position:bot.entity.position.clone(),mode:bot.game.gameMode,items:bot.inventory.items().map(i=>[i.name,i.count]).sort()}})
  bot.on('messagestr',(message,position)=>{
    if(position==='game_info') {
      state.active=message.startsWith('Your turn')
      return
    }
    state.messages.push(message)
    transcript.push({player:name,message,time:new Date().toISOString()})
    const hole=message.match(/^Hole: (\d+)\/(\d+)/)
    if(hole) {
      const nextHole=Number(hole[1])
      if(nextHole!==state.hole && name===names[0]) console.log('Playing hole '+nextHole+'/'+expectedHoles)
      state.hole=nextHole;assert.equal(Number(hole[2]),expectedHoles)
    }
    if(message==='Round complete!') state.completed=true
  })
  return bot
}
async function playTurn(bot) {
  const state=states.get(bot);state.lock=true
  try {
    const before=state.messages.length
    command(bot,'status')
    await until(()=>state.messages.slice(before).some(m=>/^Hole:/.test(m)), 'hole status',10000)
    const hole=state.hole, previous=state.shots.get(hole)||0
    // Two throws in doubles verify that teammates alternate; FFA throws once before conceding.
    if(previous >= 1) {
      command(bot,'concede')
      await pause(1200)
      return
    }
    command(bot,'lie');await pause(350)
    const targetX=(1+((hole-1)%3)*7+5)*16+.5+8
    const targetZ=(1+Math.floor((hole-1)/3)*7+((hole-1)%2===0?5:1))*16+.5+8
    bot.setQuickBarSlot(2)
    await bot.lookAt(new Vec3(targetX,bot.entity.position.y+18,targetZ),true)
    const armed=state.messages.length
    bot.activateItem()
    await until(()=>state.messages.slice(armed).some(m=>m.includes('Throw meter started')), 'hotbar meter',10000)
    await pause(1500)
    bot.swingArm('right')
    await until(()=>state.messages.slice(armed).some(m=>m==='Disc released.'), 'shot release',10000)
    state.shots.set(hole,previous+1)
    // A second command during flight must never start a second shot.
    const overlapping=state.messages.length
    bot.chat('/throwdisc putter')
    await until(()=>state.messages.slice(overlapping).some(m=>m.includes('Wait for the disc to land.')||m.includes('not your')), 'in-flight rejection',10000)
    results.push({player:bot.username,hole,shot:'released',overlap:'rejected'})
    await pause(5500)
  } finally {state.lock=false}
}
async function main() {
  names.forEach(connect)
  await until(()=>bots.every(b=>states.get(b).ready), 'client connections')
  await pause(1200)
  for(const b of bots.slice(1)) {
    command(bots[0],'party invite '+b.username);await pause(350)
    command(b,'party accept');await pause(350)
  }
  command(bots[0],'queue '+format)
  const deadline=Date.now()+Math.max(240000,expectedHoles*(format==='doubles'?60000:35000))
  while(!bots.every(b=>states.get(b).completed)) {
    if(Date.now()>deadline) throw Error('Round did not complete')
    for(const b of bots) {
      const state=states.get(b)
      if(state.error) throw Error(b.username+': '+state.error)
      if(state.active && !state.lock && b.game.gameMode==='adventure') await playTurn(b)
    }
    await pause(150)
  }
  await pause(1500)
  for(const b of bots) {
    const state=states.get(b)
    assert.equal(b.game.gameMode,state.original.mode,'game mode restored')
    assert.ok(b.entity.position.distanceTo(state.original.position)<2,'original location restored')
    assert.deepEqual(b.inventory.items().map(i=>[i.name,i.count]).sort(),state.original.items,'inventory restored')
    assert.ok(state.messages.some(m=>m==='Final standings:'),'final standings announced')
    assert.ok(state.previewFrames>0 && state.maxPreviewPitch<=35.01,'preview pitch stays bounded on the client')
  }
  assert.ok(results.length>=expectedHoles*2,'network throws covered every hole')
  if(format==='doubles') for(const b of bots) assert.ok(states.get(b).shots.size>0,'each doubles teammate threw')
  console.log('PASS: '+format+' '+expectedHoles+' holes, '+bots.length+' connected clients, '+results.length+' shots, flight lock, final standings, and restoration.')
  finished=true
}
main().catch(error=>{console.error(error.stack);process.exitCode=1}).finally(()=>{
  fs.mkdirSync('build',{recursive:true})
  fs.writeFileSync('build/multiplayer-'+format+'-'+expectedHoles+'.json',JSON.stringify({passed:finished,results,transcript},null,2))
  bots.forEach(b=>b.quit())
  setTimeout(()=>process.exit(process.exitCode||0),1000)
})
