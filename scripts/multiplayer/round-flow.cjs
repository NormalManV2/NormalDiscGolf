const mineflayer = require('mineflayer')
const fs = require('node:fs')
const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { Vec3 } = require('vec3')
const { attachClientMovementTicks } = require('./parkland-41.cjs')
const format = process.argv[2] || 'ffa'
const expectedHoles = Number(process.argv[3] || 18)
const expectLocalPack = process.argv.includes('--expect-local-pack')
const configuredPackUrl = process.env.NDG_TEST_PACK_URL
const expectPack = expectLocalPack || Boolean(configuredPackUrl)
const expectSkillXp = process.argv.includes('--expect-skill-xp')
const expectTrickShots = process.argv.includes('--expect-trick-shots')
const expectShotControls = process.argv.includes('--expect-shot-controls') || expectTrickShots
const expectRoller = process.argv.includes('--expect-roller')
const flyoverHazard = process.env.NDG_EXPECT_FLYOVER_HAZARD
const queueTerrain = process.env.NDG_TEST_QUEUE_TERRAIN
const hazardColors = { Water: 0x4ad3ff, Lava: 0xff6c32, Void: 0xc57dff }
const showcaseHints = {
  woodland: {
    'Cathedral Run': /Hold the center of the pine corridor/,
    Fernbend: /Aim for the bend, then turn (left|right)/,
    Mossgate: /Aim through the gap in the fairway/
  },
  mountain: {
    'Glacier Pass': /Aim between the blue ice walls/,
    'Eagle Ridge': /Follow the ridge above the drop/,
    'Summit Steps': /Aim uphill to the raised basket/
  },
  desert: {
    'Sandstone Gate': /Keep the drive low through the arch/,
    'Painted Rim': /Follow the fairway below the canyon rim/,
    'Split Mesa': /Follow the gold line or play the other rim/
  },
  island: {
    'Wreck Lagoon': /Carry the lagoon along the gold line/,
    'Lantern Cay': /Carry the water to the basket island/,
    'Palm Cove': /Play to the bend before turning to the green/
  },
  nether: {
    'Basalt Gate': /Thread the basalt pillars/,
    'Ember Crossing': /Carry the lava to the far bank/,
    'Ashen Keep': /Hold the center on the approach to the keep/
  },
  sky: {
    'Cloud Crossing': /Follow the gold line across the gap/,
    'Driftwood Green': /Carry the gap to the basket island/,
    'Sky Crown': /Aim up the ridge to the raised basket/
  }
}
const showcase = Object.keys(showcaseHints).find(terrain => process.argv.includes('--expect-'+terrain+'-visuals'))
const carrySurfaces = {
  'Cloud Crossing': 'air',
  'Driftwood Green': 'air',
  'Wreck Lagoon': 'water',
  'Lantern Cay': 'water',
  'Ember Crossing': 'lava'
}
const trickColors = {flex:0xffff00,tomahawk:0xff00ff,thumber:0xae7aff}
const tricks = Object.keys(trickColors)
const port = Number(process.env.NDG_TEST_PORT || 25569)
const version = process.env.NDG_TEST_VERSION || '1.21.8'
const legacyPackRequest = version === '1.19.4'
const bestShot = format === 'best-shot'
const teamFormat = format === 'doubles' || bestShot
const names = teamFormat ? ['NDG_Alice','NDG_Bob','NDG_Cara','NDG_Drew'] : ['NDG_Alice','NDG_Bob']
const bots = [], transcript = [], states = new Map(), results = []
const pause = ms => new Promise(r => setTimeout(r,ms))
const until = async (predicate, label, ms=90000) => {
  const deadline=Date.now()+ms
  while(!predicate()) { if(Date.now()>deadline) throw Error('Timed out: '+label); await pause(100) }
}
const command = (bot,text) => bot.chat('/ndg '+text)
let finished = false
function connect(name) {
  const bot=mineflayer.createBot({host:'127.0.0.1',port,username:name,version,auth:'offline',physicsEnabled:false})
  attachClientMovementTicks(bot)
  const state={messages:[],ready:false,active:false,hole:0,shots:new Map(),completed:false,lock:false,previewFrames:0,maxPreviewPitch:0,aimParticles:0}
  states.set(bot,state);bots.push(bot)
  bot.on('error',error=>{state.error=error.message})
  bot.on('kicked',reason=>{state.error=JSON.stringify(reason)})
  if (showcase) recordShowcasePreviews(bot, state, showcaseHints[showcase])
  if (flyoverHazard) {
    state.flyoverDots = new Map()
    bot._client.on('world_particles', packet => {
      if (bot.game.gameMode !== 'spectator' || packet.particle.type !== 'dust') return
      const color = packet.particle.data.color & 0xffffff
      if (!state.flyoverDots.has(color)) state.flyoverDots.set(color, new Set())
      state.flyoverDots.get(color).add([packet.x,packet.y,packet.z].join(','))
    })
  }
  if (expectShotControls) bot._client.on('world_particles', packet => {
    const capture = state.trickCapture
    const point = [packet.x,packet.y,packet.z]
    if (packet.particle.type === 'dust') {
      state.aimParticles++
      if (capture?.locked && (packet.particle.data.color & 0xffffff) === capture.color)
        capture.preview.push(point)
    } else if (capture && packet.particle.type === 'end_rod') {
      capture.flight.push(point)
    }
  })
  if (expectPack) bot._client.on(legacyPackRequest ? 'resource_pack_send' : 'add_resource_pack', async pack => {
    try {
      const url = new URL(pack.url)
      if (configuredPackUrl) assert.equal(url.href, configuredPackUrl, 'pack uses the configured address')
      else assert.equal(url.hostname, '127.0.0.1', 'pack uses the local server')
      assert.equal(pack.forced, true, 'pack is required')
      const response = await fetch(url)
      assert.ok(response.ok, 'pack available over HTTP')
      const bytes = Buffer.from(await response.arrayBuffer())
      assert.equal(createHash('sha1').update(bytes).digest('hex'), pack.hash, 'download matches advertised hash')
      const packId = legacyPackRequest ? {} : { uuid: pack.uuid }
      bot._client.write('resource_pack_receive', {...packId, result: 3})
      bot._client.write('resource_pack_receive', {...packId, result: 0})
      state.packReady = true
    } catch (error) { state.error = error.message }
  })
  bot.on('move',()=>{
    if(bot.game.gameMode==='spectator') {
      state.previewFrames++
      state.maxPreviewPitch=Math.max(state.maxPreviewPitch,Math.abs(bot.entity.pitch)*180/Math.PI)
    }
  })
  bot.once('spawn',()=>{state.ready=true})
  bot.on('messagestr',(message,position)=>{
    if(position==='game_info') {
      state.active=message.startsWith('Your turn')
      if (flyoverHazard && bot.game.gameMode==='spectator' && message.includes(flyoverHazard)) state.hazardHint = message
      return
    }
    state.messages.push(message)
    if (bestShot) {
      if (message.startsWith('Choose a Best Shot lie')) state.choosing = true
      if (message.includes(' selected lie ') || message.startsWith('Choice timed out.')) state.choosing = false
    }
    if (state.trickCapture) {
      if (message.startsWith('Power locked:')) state.trickCapture.locked = true
      if (message === 'Disc released.') state.trickCapture.locked = false
    }
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

function recordShowcasePreviews(bot, state, hints) {
  state.showcasePreviews = new Map()
  state.showcaseSigns = new Map()
  state.showcaseHints = new Set()
  let preview
  bot._client.on('set_title_subtitle', packet => {
    const text = JSON.stringify(packet.text)
    for (const [layout, hint] of Object.entries(hints)) if (hint.test(text)) state.showcaseHints.add(layout)
  })
  bot._client.on('set_title_text', packet => {
    const text = JSON.stringify(packet.text)
    const layout = Object.keys(hints).find(name => text.includes(name))
    if (!layout) return
    preview = {dots: new Map(), frames: 0, checkedFrames: 0, blockedFrames: 0,
      carryDots: new Set(), carrySurface: carrySurfaces[layout]}
    state.showcasePreviews.set(layout, preview)
  })
  bot._client.on('entity_metadata', packet => {
    const text = JSON.stringify(packet.metadata)
    const layout = Object.keys(hints).find(name => text.includes(name))
    if (layout && text.includes('HOLE')) state.showcaseSigns.set(layout, text)
  })
  bot._client.on('world_particles', packet => {
    if (!preview || bot.game.gameMode !== 'spectator' || packet.particle.type !== 'dust') return
    if ((packet.particle.data.color & 0xffffff) !== 0xffcd5f) return
    const point = [packet.x, packet.z]
    preview.dots.set(point.join(','), point)
    if (preview.carrySurface && !preview.carryDots.has(point.join(','))) {
      const location = new Vec3(packet.x, packet.y, packet.z)
      if (preview.carrySurface === 'air' ? bot.blockAt(location.offset(0, -5, 0))?.name === 'air'
        : hasLiquidBelow(bot, location, preview.carrySurface)) preview.carryDots.add(point.join(','))
    }
  })
  bot.on('move', () => {
    if (!preview || bot.game.gameMode !== 'spectator') return
    preview.frames++
    const block = bot.blockAt(bot.entity.position.offset(0, 1.62, 0))
    if (block) preview.checkedFrames++
    if (block?.boundingBox === 'block') preview.blockedFrames++
  })
}

function hasLiquidBelow(bot, position, liquid) {
  for (let depth = 1; depth <= 18; depth++) {
    const block = bot.blockAt(position.offset(0, -depth, 0))
    if (block?.name === liquid) return true
    if (!block || block.boundingBox === 'block') break
  }
  return false
}

function checkShowcasePreviews(state, hints) {
  for (const [layout, hint] of Object.entries(hints)) {
    const sign = state.showcaseSigns.get(layout)
    assert.ok(sign, layout+' tee sign reached the client')
    assert.match(sign, /Par.*blocks/, 'the sign shows par and distance')
    assert.match(sign, hint, 'the sign shows the intended shot')
    const preview = state.showcasePreviews.get(layout)
    assert.ok(preview?.frames >= 40, layout+' flyover follows a moving camera')
    if (showcase !== 'woodland') {
      assert.ok(preview.checkedFrames >= 20, layout+' camera is checked against loaded terrain')
      assert.equal(preview.blockedFrames, 0, layout+' keeps the camera out of solid blocks')
    }
    assert.ok(state.showcaseHints.has(layout), 'the same cue appears in the flyover')
    const dots = [...preview.dots.values()]
    assert.ok(dots.length >= 9, layout+' sends a visible route trail')
    const [[x,z], [nextX,nextZ]] = dots
    const span = Math.hypot(nextX-x,nextZ-z)
    const offset = Math.max(...dots.map(([a,b]) => Math.abs((nextX-x)*(b-z)-(nextZ-z)*(a-x))/span))
    if (layout === 'Fernbend' || layout === 'Palm Cove') assert.ok(offset > 8, 'the trail turns at the dogleg')
    else if (showcase === 'woodland' || ['Sandstone Gate','Glacier Pass','Basalt Gate'].includes(layout))
      assert.ok(offset < .01, layout+' shows a straight line through its landmark')
    if (preview.carrySurface)
      assert.ok(preview.carryDots.size >= 2, layout+' shows gold markers suspended over '+preview.carrySurface)
  }
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
    bot.setQuickBarSlot(2)
    await bot.look(bot.entity.yaw, Math.PI / 15, true)
    if (expectShotControls) {
      command(bot,'shot timing on')
      await until(()=>state.messages.some(m=>m.includes('timing on')), 'enable release timing',10000)
    }
    const trick = expectTrickShots ? tricks[((hole - 1) * names.length + names.indexOf(bot.username)) % tricks.length] : null
    const stance = trick || (expectRoller ? 'roller' : null)
    if (stance) {
      const setup = state.messages.length
      command(bot,'shot stance '+stance)
      await until(()=>state.messages.slice(setup).some(m=>m.includes(stance+' stance')), 'enable '+stance+' stance',10000)
    }
    if (trick) state.trickCapture = { color:trickColors[trick], locked:false, preview:[], flight:[] }
    const beforeParticles = new Map(bots.map(client=>[client,states.get(client).aimParticles]))
    const armed=state.messages.length
    let releaseQuality
    bot.activateItem()
    await until(()=>state.messages.slice(armed).some(m=>m.includes('Throw meter started')), 'hotbar meter',10000)
    if (expectShotControls) {
      await until(()=>state.aimParticles > beforeParticles.get(bot), 'aiming particles',10000)
      for (const other of bots.filter(client=>client!==bot))
        assert.equal(states.get(other).aimParticles,beforeParticles.get(other),'aiming particles are private')
      const angled = state.messages.length
      bot._client.write('player_input',{inputs:{shift:true}})
      bot._client.write('block_dig',{status:6,location:{x:0,y:0,z:0},face:0,sequence:0})
      bot._client.write('player_input',{inputs:{shift:false}})
      await until(()=>state.messages.slice(angled).some(m=>m.startsWith('Release angle:')), 'sneak plus swap changes angle',10000)
      if (stance) await waitForStancePower(bot,stance)
      bot.swingArm('right')
      await until(()=>state.messages.slice(armed).some(m=>m.startsWith('Power locked:')), 'first click locks power',10000)
      assert.ok(!state.messages.slice(armed).includes('Disc released.'),'first click does not release')
      let centered = false
      const releaseInGreen = bar => {
        if (centered || !String(bar.title).includes('Release Timing') || Math.abs(bar.health - .5) > .02) return
        centered = true
        bot.swingArm('right')
      }
      bot.on('bossBarUpdated',releaseInGreen)
      try {
        await until(()=>centered,'green timing window',10000)
      } finally {
        bot.off('bossBarUpdated',releaseInGreen)
      }
      await until(()=>(releaseQuality=state.messages.slice(armed).find(message=>/^(Clean|Early|Late) release$/.test(message))),
        'release timing feedback on hole '+hole,10000)
    } else {
      if (stance) await waitForStancePower(bot,stance)
      else await pause(1500)
      bot.swingArm('right')
    }
    await until(()=>state.messages.slice(armed).some(m=>m==='Disc released.'), 'shot release',10000)
    state.shots.set(hole,previous+1)
    // A second command during flight must never start a second shot.
    const overlapping=state.messages.length
    bot.chat('/throwdisc putter')
    await until(()=>state.messages.slice(overlapping).some(m=>m.includes('Wait for the disc to land.')||m.includes('not your')), 'in-flight rejection',10000)
    const result = {player:bot.username,hole,shot:'released',overlap:'rejected',stance,releaseQuality}
    results.push(result)
    if (trick) {
      // Changing the next shot while the disc flies must leave the current throw's XP intact.
      const switched = state.messages.length
      command(bot,'shot stance normal')
      await until(()=>state.messages.slice(switched).some(m=>m.includes('normal stance')), 'switch next shot during flight',10000)
      await until(()=>state.messages.slice(armed).some(m=>m.startsWith('Skill XP:')), 'trick shot resolves',10000)
      const awards = state.messages.slice(armed).filter(m=>m.startsWith('Skill XP:'))
      assert.equal(awards.length,1,'one resolved XP award')
      assert.deepEqual(tricks.filter(name=>awards[0].includes(name+' +')),[trick],'only the launched trick gets XP')
      assert.ok(state.messages.indexOf(awards[0],armed) > state.messages.findIndex((m,i)=>i>=switched && m.includes('normal stance')),
        'stance changed before the old shot resolved')
      result.matchedPreviewPoints = assertPreviewMatchesFlight(state.trickCapture,trick)
    }
    if (expectRoller) {
      await until(()=>state.messages.slice(armed).includes('Disc is rolling'), 'live disc enters a roll',10000)
      await until(()=>state.messages.slice(armed).some(m=>m.startsWith('Skill XP:') && m.includes('roller +')),
        'roller XP after landing',10000)
    }
    if (expectSkillXp) {
      await until(()=>state.messages.slice(armed).filter(m=>m.startsWith('Skill XP:')).length===1,
        'one XP award for the resolved throw',10000)
    } else await pause(5500)
    if (bestShot) await pause(1000)
  } finally {
    state.trickCapture = null
    state.lock = false
  }
}
async function waitForStancePower(bot,stance) {
  await until(()=>Object.values(bot.bossBars).some(bar=>String(bar.title).toLowerCase().startsWith(stance+' power') && bar.health >= .55),
    'usable '+stance+' power',10000)
}
function assertPreviewMatchesFlight(capture,trick) {
  const unique = [...new Map(capture.preview.map(point=>[point.join(','),point])).values()]
  assert.ok(unique.length>=3,trick+' sends a colored preview at locked power')
  const distance = (a,b)=>Math.hypot(...a.map((value,i)=>value-b[i]))
  const matched = unique.filter(point=>capture.flight.some(sample=>distance(point,sample)<.4))
  assert.ok(matched.length>=3,trick+' preview follows the actual disc path')
  return matched.length
}
async function main() {
  assert.ok(!(expectRoller && expectTrickShots),'run roller and trick checks separately')
  assert.ok(!expectTrickShots || names.length*expectedHoles>=tricks.length,'use four players or at least two holes to cover all tricks')
  assert.ok(!showcase || (format==='ffa' && expectedHoles===3), 'use a three-hole FFA showcase fixture')
  assert.ok(!flyoverHazard || hazardColors[flyoverHazard], 'use Water, Lava, or Void to name the expected main hazard')
  names.forEach(connect)
  await until(()=>bots.every(b=>states.get(b).ready), 'client connections')
  if (expectPack) await until(()=>{
    for (const state of states.values()) if (state.error) throw Error(state.error)
    return bots.every(b=>states.get(b).packReady)
  }, 'required pack', 15000)
  await pause(1200)
  for (const bot of bots) {
    const state = states.get(bot)
    const before = state.messages.length
    command(bot,'party leave')
    command(bot,'party status')
    await until(()=>state.messages.slice(before).includes('You are not in a party.'),'clear previous test party',10000)
  }
  for(const b of bots.slice(1)) {
    command(bots[0],'party invite '+b.username);await pause(350)
    command(b,'party accept');await pause(350)
  }
  for (const bot of bots) {
    states.get(bot).original = {
      position:bot.entity.position.clone(),
      mode:bot.game.gameMode,
      items:bot.inventory.items().map(i=>[i.name,i.count]).sort()
    }
  }
  command(bots[0],'queue '+format+(queueTerrain ? ' new '+queueTerrain : ''))
  await until(()=> {
    const state = states.get(bots[0])
    if (state.error) throw Error(state.error)
    const rejected = state.messages.find(m=>m.includes('must finish loading the server resource pack'))
    if (rejected) throw Error(rejected)
    return state.messages.some(m=>m.startsWith('Your party has been queued') || m.startsWith('You have been queued'))
  }, 'queue confirmation',10000)
  const deadline=Date.now()+Math.max(240000,expectedHoles*(teamFormat?60000:35000))
  while(!bots.every(b=>states.get(b).completed)) {
    if(Date.now()>deadline) throw Error('Round did not complete')
    for(const b of bots) {
      const state=states.get(b)
      if(state.error) throw Error(b.username+': '+state.error)
      if (state.choosing) {
        state.choosing = false
        command(b,'lie 1')
        await pause(1200)
      } else if(state.active && !state.lock && b.game.gameMode==='adventure') await playTurn(b)
    }
    await pause(150)
  }
  await pause(1500)
  for(const b of bots) {
    const state=states.get(b)
    assert.equal(b.game.gameMode,state.original.mode,'game mode restored')
    assert.ok(b.entity.position.distanceTo(state.original.position)<2,
      b.username+' returned to '+b.entity.position+' from original '+state.original.position)
    assert.deepEqual(b.inventory.items().map(i=>[i.name,i.count]).sort(),state.original.items,'inventory restored')
    assert.ok(state.messages.some(m=>m==='Final standings:'),'final standings announced')
    assert.ok(state.previewFrames>0 && state.maxPreviewPitch<=35.01,'preview pitch stays bounded on the client')
    if (showcase) checkShowcasePreviews(state, showcaseHints[showcase])
    if (flyoverHazard) {
      assert.match(state.hazardHint ?? '', new RegExp(flyoverHazard+' (carry|beside the fairway).*Sneak to skip'), 'the preview names the hazard')
      assert.ok(state.flyoverDots.get(0xffcd5f)?.size >= 5, 'gold route markers reach the viewer')
      assert.ok(state.flyoverDots.get(hazardColors[flyoverHazard])?.size >= 8, 'the main hazard is marked in its own color')
      assert.ok(state.flyoverDots.get(0xecffbf)?.size >= 12, 'the camera ends at a marked basket')
    }
  }
  assert.ok(results.length>=expectedHoles*2,'network throws covered every hole')
  if(teamFormat) for(const b of bots) assert.ok(states.get(b).shots.size>0,'each doubles teammate threw')
  if (expectShotControls) for (const b of bots) {
    const releases = results.filter(result=>result.player===b.username)
    assert.ok(releases.some(result=>result.releaseQuality==='Clean release'),b.username+' reached the clean timing window')
    console.log(b.username+': '+releases.filter(result=>result.releaseQuality==='Clean release').length+'/'+releases.length+' clean releases')
  }
  if(bestShot) assert.ok(transcript.some(entry=>entry.message.includes(' selected lie ')),'played a chosen lie across holes')
  if(expectTrickShots) assert.deepEqual([...new Set(results.map(result=>result.stance))].sort(),[...tricks].sort(),'all tricks tested')
  if(showcase) console.log('PASS: both clients received '+showcase+' tee signs, shot cues, and route particles on all three moving flyovers.')
  if(flyoverHazard) console.log('PASS: both clients received '+flyoverHazard.toLowerCase()+' guidance and separate route, hazard, and basket markers.')
  console.log('PASS: '+format+' '+expectedHoles+' holes, '+bots.length+' connected clients, '+results.length+' shots, flight lock, final standings, and restoration.')
  finished=true
}
main().catch(error=>{console.error(error.stack);process.exitCode=1}).finally(()=>{
  fs.mkdirSync('build',{recursive:true})
  fs.writeFileSync('build/multiplayer-'+format+'-'+expectedHoles+'.json',JSON.stringify({passed:finished,results,transcript},null,2))
  bots.forEach(b=>b.quit())
  setTimeout(()=>process.exit(process.exitCode||0),1000)
})
