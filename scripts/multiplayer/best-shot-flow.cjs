const assert = require('node:assert/strict')
const { once } = require('node:events')
const { Vec3 } = require('vec3')
const { tee, basket, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

const players = ['A1','A2','B1','B2'].map(side => connect('BS'+side+(process.pid % 10000)))
const teams = [players.slice(0,2),players.slice(2)]
const observer = players[0]
const latestThrower = () => players.find(player => player.name === observer.turns.at(-1))
const shotTeam = () => teams.find(team => team.includes(latestThrower()))
const ballPosition = state => state.bot.entity.position.clone()
const sameLie = (first,second) => first.distanceTo(second)<.08

for (const state of players) {
  state.choices = new Map()
  state.markers = new Map()
  state.bot.on('message', message => {
    const choice = JSON.stringify(message.json).match(/\/ndg lie ([12]) ([a-f0-9-]{36})/)
    if (choice) state.choices.set(Number(choice[1]),choice[0])
  })
  state.bot._client.on('world_particles', packet => {
    if (packet.amount!==6 || packet.particle.type!=='dust') return
    const rgb = packet.particle.data.color & 0xffffff
    const choice = rgb===0x00ffff ? 1 : rgb===0xffff00 ? 2 : null
    if (choice) state.markers.set(choice,new Vec3(packet.x,packet.y-.85,packet.z))
  })
}

async function invitePair([captain, partner]) {
  await command(captain,'party invite '+partner.name,/Party invite sent/)
  await command(partner,'party accept',/joined the party/)
}

async function waitForPlayer(action, response) {
  const before = observer.turns.length
  await action()
  await until(() => observer.turns.length > before, 'next Best Shot thrower')
  if (response) assert.ok(response(latestThrower()), 'the correct teammate or opponent throws next')
}

async function returnToLie(state) {
  await command(state,'lie')
  await pause(230)
  return ballPosition(state)
}

async function checkStrokes(team, strokes) {
  for (const state of team)
    await command(state,'score text',new RegExp('^Current hole team strokes: '+strokes+'$'))
}

async function pickFromPair(timeout) {
  const team = shotTeam()
  const first = latestThrower()
  const second = team.find(state=>state!==first)
  const origin = await returnToLie(first)
  await waitForPlayer(() => release(first,shortShot(first.bot)), state => state===second)
  assert.ok(sameLie(await returnToLie(second),origin), 'teammates throw from the original shared lie')
  await checkStrokes(team,0)
  const before = first.messages.length
  for (const player of players) {
    player.choices.clear()
    player.markers.clear()
  }
  await release(second,shortShot(second.bot,45,-.20))
  await until(() => first.messages.slice(before).some(line=>line.startsWith('Choose a Best Shot lie')),
    'choice opens after the second throw')
  await until(() => team.every(state=>state.choices.has(1) && state.choices.has(2) && state.markers.has(1) && state.markers.has(2)),
    'both teammates get clickable choices and markers')
  assert.ok(!sameLie(first.markers.get(1),first.markers.get(2)), 'the two choices have distinct positions')
  assert.ok(teams.flat().filter(state=>!team.includes(state)).every(state=>state.choices.size===0),
    'lie options are private to this team')
  await checkStrokes(team,0)
  const next = observer.turns.length
  let selected
  if (timeout) {
    selected = [...first.markers.values()].sort((a,b)=>a.distanceTo(basket)-b.distanceTo(basket))[0]
    await until(() => observer.messages.some(line=>line.startsWith('Choice timed out. Best Shot selected the closer lie')),
      'choice timeout picks the nearest legal lie')
  } else {
    const opponent = players.find(state=>!team.includes(state))
    await command(opponent,'lie 2',/Your team does not have a lie/)
    await command(first,'lie 2 expired-button',/That lie choice expired/)
    const chosenNumber = first.markers.get(1).distanceTo(basket)>first.markers.get(2).distanceTo(basket) ? 1 : 2
    selected = first.markers.get(chosenNumber)
    await command(second,second.choices.get(chosenNumber).slice('/ndg '.length),/selected lie/)
    await command(first,'lie 1',/Your team does not have a lie/)
  }
  await until(() => observer.turns.length>next && !team.includes(latestThrower()),'other team gets its turn after the choice')
  await checkStrokes(team,1)
  for (const state of team)
    assert.ok(sameLie(await returnToLie(state),selected),state.name+' can return to the selected shared lie')
  return {team,selected}
}

async function sinkFromLie(result, useSecond) {
  const first = latestThrower()
  assert.ok(result.team.includes(first),'the selected team is up')
  assert.ok(sameLie(await returnToLie(first),result.selected),'next stroke starts at the selected lie')
  if (useSecond) {
    await waitForPlayer(() => release(first,shortShot(first.bot)), state=>state!==first && result.team.includes(state))
    assert.ok(sameLie(await returnToLie(latestThrower()),result.selected),'the second attempt starts from the same chosen lie')
  }
  const shooter = latestThrower()
  const shot = await basketShot(shooter.bot)
  assert.ok(shot,'a scoring line is available from '+ballPosition(shooter))
  await release(shooter,shot)
  await until(() => observer.messages.some(line=>line.startsWith(shooter.name+' completed hole 1!')), 'live basket scores')
}

async function main() {
  await until(() => players.every(state=>state.spawned),'four clients connect')
  await invitePair(teams[0])
  await invitePair(teams[1])
  const menu = once(observer.bot,'windowOpen')
  await command(observer,'menu')
  const [window] = await menu
  await until(() => window.slots[16]?.name==='lime_dye','Best Shot in the round picker')
  await clickModeJoin(observer, 16)
  await until(() => observer.messages.includes('Your party has been queued for Best Shot Doubles.'),'first party queues')
  await pause(3500)
  assert.equal(observer.bot.game.gameMode,observer.original.mode,'one pair waits for an opponent')
  await command(teams[1][0],'queue best-shot',/Your party has been queued for Best Shot Doubles/)
  await until(() => players.every(state=>state.messages.some(line=>line.startsWith('Best Shot Doubles.')))
    && players.every(state=>state.bot.game.gameMode==='adventure') && latestThrower(), 'round starts with four players',120000)
  await pause(500)
  assert.ok(ballPosition(latestThrower()).distanceTo(tee)<2,'seed 41 opening tee')
  for (const state of players) await command(state,'shot timing on',/timing on/)

  const first = await pickFromPair(false)
  console.log('PASS: shared tee, private choices, teammate click, rejection of invalid choices, and one stroke')
  const second = await pickFromPair(true)
  console.log('PASS: timeout used the closer lie and both teammates could return to it')
  const beforeBasket = observer.turns.length
  await sinkFromLie(first,true)
  await until(() => observer.turns.length>beforeBasket && second.team.includes(latestThrower()),'next team after basket')
  await sinkFromLie(second,false)

  await until(() => players.every(state=>state.messages.includes('Round complete!')),'round finishes from both baskets')
  await pause(600)
  for (const {bot,original,messages} of players) {
    assert.equal(messages.filter(line=>/^\d\. .*\(2 strokes\)$/.test(line)).length,2,'both teams finish in two shared strokes')
    assert.ok(messages.some(line=>line.startsWith('Round reward: +')),'basket credits paid')
    assert.equal(bot.game.gameMode,original.mode,'game mode restored')
    assert.ok(ballPosition({bot}).distanceTo(original.position)<2,'lobby position restored')
    assert.deepEqual(inventory(bot),original.items,'inventory restored')
  }
  const opened = once(observer.bot,'windowOpen')
  await command(observer,'clubhouse')
  const [records] = await opened
  await until(() => records.slots[16]?.name==='written_book','clubhouse record button')
  await observer.bot.clickWindow(16,0,0)
  await until(() => records.slots[53]?.name==='lime_dye','Best Shot records tab')
  await observer.bot.clickWindow(53,0,0)
  await until(() => records.slots[9]?.name==='paper','new Best Shot scorecard appears')
  console.log('PASS: four-client Best Shot Doubles completed with two real baskets, final scores, rewards, a Clubhouse record, and restoration')
}

main().catch(error => { console.error(error); process.exitCode=1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  await pause(750)
  process.exit(process.exitCode || 0)
})
