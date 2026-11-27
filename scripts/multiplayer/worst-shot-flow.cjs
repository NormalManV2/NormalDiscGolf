const assert = require('node:assert/strict')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { tee, basket, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const players = ['A1','A2','B1','B2'].map(side=>connect('WS'+side+(process.pid % 10000)))
const teams = [players.slice(0,2),players.slice(2)]
const observer = players[0]
const active = () => players.find(state=>state.name===observer.turns.at(-1))
const activeTeam = () => teams.find(team=>team.includes(active()))
const close = (a,b) => a.distanceTo(b)<.08

for (const state of players) {
  state.bot._client.on('world_particles', packet => {
    if (packet.particle.type==='end_rod') state.landing = new Vec3(packet.x,packet.y,packet.z)
  })
}

async function invite([lead,partner]) {
  await command(lead,'party invite '+partner.name,/Party invite sent/)
  await command(partner,'party accept',/joined the party/)
}

async function lie(state) {
  await command(state,'lie')
  await pause(230)
  return state.bot.entity.position.clone()
}

async function throwAndWait(state,shot,isNext) {
  assert.ok(shot,'a basket line from '+state.bot.entity.position)
  const turn = observer.turns.length
  state.landing = null
  await release(state,shot)
  await until(()=>observer.turns.length>turn || observer.messages.includes('Round complete!'),'turn or round completion')
  if (isNext) assert.ok(isNext(active()),'the correct player throws next')
}

async function checkStrokes(team,strokes) {
  for (const state of team)
    await command(state,'score text',new RegExp('^Current hole team strokes: '+strokes+'$'))
}

async function openTeamStroke(withBasket) {
  const team = activeTeam()
  const first = active()
  const second = team.find(state=>state!==first)
  const origin = await lie(first)
  const before = observer.messages.length
  await throwAndWait(first,withBasket ? await basketShot(first.bot) : shortShot(first.bot), state=>state===second)
  const firstLanding = first.landing
  assert.ok(close(await lie(second),origin),'second player throws from the same shared lie')
  await checkStrokes(team,0)
  if (withBasket) {
    assert.ok(observer.messages.slice(before).some(line=>line.startsWith(first.name+' reached the basket.')))
    assert.ok(!observer.messages.slice(before).some(line=>line.includes('completed hole')),'a lone basket leaves the hole open')
  }
  await throwAndWait(second,withBasket ? shortShot(second.bot) : shortShot(second.bot,45,-.20), state=>!team.includes(state))
  const expected = withBasket ? second : first
  const expectedLanding = withBasket ? second.landing : firstLanding
  if (!withBasket) {
    assert.ok(firstLanding && second.landing && firstLanding.distanceTo(basket)>second.landing.distanceTo(basket)+1,
      'the first live landing is clearly farther away')
  }
  assert.ok(observer.messages.slice(before).includes('Worst Shot used '+expected.name+"'s lie."),
    'the server commits the worse result')
  await checkStrokes(team,1)
  const selected = await lie(first)
  assert.ok(close(await lie(second),selected),'both teammates can return to the committed lie')
  assert.ok(expectedLanding && selected.distanceTo(expectedLanding)<2,
    'committed lie '+selected+' is at the expected landing '+expectedLanding)
  return {team,selected}
}

async function closeHole({team,selected}) {
  const first = active()
  const second = team.find(state=>state!==first)
  assert.ok(team.includes(first) && close(await lie(first),selected),'next stroke uses the committed lie')
  await throwAndWait(first,await basketShot(first.bot),state=>state===second)
  await checkStrokes(team,1)
  assert.ok(close(await lie(second),selected),'both put the disc in from the same lie')
  await throwAndWait(second,await basketShot(second.bot))
  await until(()=>observer.messages.some(line=>line.startsWith(second.name+' completed hole 1!')),'team hole out')
}

async function main() {
  await until(()=>players.every(state=>state.spawned),'four clients connect')
  await invite(teams[0])
  await invite(teams[1])
  const menu = once(observer.bot,'windowOpen')
  await command(observer,'menu')
  const [window] = await menu
  await until(()=>window.slots[17]?.name==='orange_dye','Worst Shot in the round picker')
  await clickModeJoin(observer, 17)
  await until(()=>observer.messages.includes('Your party has been queued for Worst Shot Doubles.'),'first pair queues')
  await pause(3500)
  assert.equal(observer.bot.game.gameMode,observer.original.mode,'one pair waits for an opponent')
  await command(teams[1][0],'queue worst-shot',/Your party has been queued for Worst Shot Doubles/)
  await until(()=>players.every(state=>state.messages.some(line=>line.startsWith('Worst Shot Doubles.'))
    && state.bot.game.gameMode==='adventure') && active(),'round starts with four players',120000)
  await pause(500)
  assert.ok(active().bot.entity.position.distanceTo(tee)<2,'seed 41 fixture')
  for (const state of players) await command(state,'shot timing on',/timing on/)

  const first = await openTeamStroke(false)
  console.log('PASS: both attempts used the shared tee and the farther legal lie counted once')
  const second = await openTeamStroke(true)
  console.log('PASS: a real ace plus a miss used the miss')
  await closeHole(first)
  await closeHole(second)
  await until(()=>players.every(state=>state.messages.includes('Round complete!')),'both double baskets complete the round')
  await pause(600)
  for (const {bot,original,messages} of players) {
    assert.equal(messages.filter(line=>/^\d\. .*\(2 strokes\)$/.test(line)).length,2,'both teams finish in two strokes')
    assert.equal(messages.filter(line=>line.startsWith('Round reward: +')).length,1,'team basket pays once')
    assert.equal(bot.game.gameMode,original.mode,'game mode restored')
    assert.ok(bot.entity.position.distanceTo(original.position)<2,'original position restored')
    assert.deepEqual(inventory(bot),original.items,'inventory restored')
  }
  for (const team of teams) {
    const score = state => state.messages.find(line=>line.startsWith('Team score: '))
    const drive = state => state.messages.find(line=>line.startsWith('Longest drive: '))
    assert.equal(score(team[0]),'Team score: -1 (2 strokes, par 3) • 1/1 holes')
    assert.equal(score(team[1]),score(team[0]),'teammates see the same official score')
    for (const state of team) assert.match(drive(state),/^Longest drive: \d+\.\d blocks$/)
    assert.notEqual(drive(team[0]),drive(team[1]),'teammates keep separate tee distances')
  }
  console.log('PASS: four personal summaries report official team scores and each player’s own drives')
  console.log('PASS: four-client Worst Shot Doubles completed with two team baskets, two-stroke cards, rewards and restoration')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(async ()=>{
  players.forEach(state=>state.bot.quit())
  await pause(750)
  process.exit(process.exitCode||0)
})
