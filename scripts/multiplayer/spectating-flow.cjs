const assert = require('node:assert/strict')
const { Vec3 } = require('vec3')
const { basket, until, pause, connect, inventory, command, basketShot, shortShot, release } = require('./parkland-41.cjs')

const players = ['A', 'B'].map(side => connect('Replay' + side + process.pid % 10000))
const [leader, partner] = players
const active = () => players.find(state => state.name === leader.turns.at(-1))
const attached = state => state.bot.game.gameMode === 'spectator' && state.cameraId != null
  && state.cameraId !== state.bot.entity?.id && state.bot.entities[state.cameraId]?.name === 'armor_stand'
const onGround = state => state.bot.game.gameMode === 'adventure' && !attached(state)
const position = state => state.bot.entities[state.cameraId]?.position
const direction = (yaw, pitch) => new Vec3(Math.cos(yaw)*Math.cos(pitch), Math.sin(pitch), Math.sin(yaw)*Math.cos(pitch))

for (const state of players) state.bot._client.on('camera', packet => { state.cameraId = packet.cameraId })

async function round() {
  const turns = leader.turns.length
  await command(leader, 'queue ffa', /queued for FFA/)
  await until(() => leader.turns.length > turns && players.every(onGround), 'round starts after the flyover', 120000)
  for (const state of players) {
    await command(state, 'status', /^Hole: 1\/1$/)
    await command(state, 'shot timing on', /timing on/)
  }
}

async function lie(state) {
  await command(state, 'lie')
  await pause(240)
}

function puttShot(bot) {
  const eye = bot.entity.position.offset(0, bot.entity.eyeHeight, 0)
  const yaw = Math.atan2(basket.z-eye.z, basket.x-eye.x)
  const reaches = (power, pitch, error) => {
    const aim = direction(yaw+error, pitch)
    const point = eye.plus(aim.scaled(.35))
    const velocity = aim.scaled(1.3*1.07*.68*power)
    for (let tick=0;tick<=30;tick++) {
      for (let step=1;step<=6;step++) {
        const probe = point.plus(velocity.scaled(step/6))
        if (Math.abs(probe.x-basket.x)<1.22 && Math.abs(probe.z-basket.z)<1.22 && Math.abs(probe.y-basket.y)<.90)
          return true
        if (bot.blockAt(probe, false)?.boundingBox === 'block') return false
      }
      point.add(velocity)
      velocity.scale(.96)
      velocity.y -= .014
    }
    return false
  }
  for (const percent of [95,90,100,85,80,75,70,65,60,55,50,45,40]) {
    for (let pitch=.36;pitch>=-.25;pitch-=.015)
      if ([-.014,0,.014].every(error => reaches(percent/100,pitch,error)))
        return { power:percent, aim:eye.plus(direction(yaw,pitch).scaled(10)) }
  }
  return null
}

async function seeReplay(state, from, scorer, label) {
  await until(() => state.messages.slice(from).some(line => line.startsWith('Instant replay: ' + scorer.name + ' - ' + label))
    && attached(state), label + ' replay camera')
}

async function movingCamera(state) {
  const start = position(state).clone()
  await until(() => attached(state) && position(state).distanceTo(start) > 2, 'camera follows the moving shot', 2500)
}

async function restored(states = players) {
  await until(() => states.every(({ bot, original }) => bot.game.gameMode === original.mode
    && bot.entity.position.distanceTo(original.position) < 2), 'lobby position and mode restored')
  for (const state of states) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    assert.ok(state.cameraId == null || state.cameraId === state.bot.entity.id, 'client camera returns to its own player')
  }
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients join')
  await command(leader, 'party invite ' + partner.name, /Party invite sent/)
  await command(partner, 'party accept', /joined the party/)
  await round()
  for (const state of players) {
    await command(state, 'camera', /^Disc camera: off for this round\.$/)
    await command(state, 'camera on', /^Disc camera: on/)
  }

  const ace = active(), putter = players.find(state => state !== ace)
  await lie(ace)
  const aimed = await basketShot(ace.bot)
  assert.ok(aimed, 'a clear ace line from the tee')
  const origins = players.map(state => state.bot.entity.position.clone())
  const marks = players.map(state => state.messages.length)
  const first = release(ace, aimed)
  await until(() => players.every(attached), 'both clients enter the live disc camera')
  assert.equal(players[0].cameraId, players[1].cameraId, 'both players follow one camera anchor')
  const liveAnchor = ace.cameraId
  await movingCamera(ace)
  await command(putter, 'skip')
  await until(() => onGround(putter) && putter.bot.entity.position.distanceTo(origins[players.indexOf(putter)]) < .5,
    'skip restores the spectator to the saved location')
  assert.ok(attached(ace), 'the other player stays in the live camera')
  await first
  await Promise.all(players.map((state, index) => seeReplay(state, marks[index], ace, 'Ace!')))
  assert.notEqual(ace.cameraId, liveAnchor, 'ace uses a separate playback camera')
  await movingCamera(ace)
  putter.bot._client.write('player_input', { inputs:{ shift:true } })
  await until(() => onGround(putter), 'sneak exits replay independently')
  putter.bot._client.write('player_input', { inputs:{ shift:false } })
  assert.ok(attached(ace), 'the other player keeps watching the ace')
  await until(() => players.every(onGround) && active() === putter, 'normal play resumes after the ace replay')
  console.log('PASS: both players followed a real ace, skipped independently, and saw its recorded flight')

  for (const state of players) await command(state, 'camera off', /^Disc camera: off/)
  await command(ace, 'replay off', /^Instant replays: off/)
  await lie(putter)
  const eye = putter.bot.entity.position.offset(0, putter.bot.entity.eyeHeight, 0)
  const heading = Math.atan2(basket.z-eye.z, basket.x-eye.x)
  const beforeApproach = putter.messages.length, turns = leader.turns.length
  await release(putter, { power:45, aim:eye.plus(direction(heading+.31,.12).scaled(10)) })
  await until(() => active() === putter && leader.turns.length > turns && players.every(onGround), 'ordinary approach advances without playback')
  assert.ok(!putter.messages.slice(beforeApproach).some(line => line.startsWith('Instant replay:')))
  await lie(putter)
  const distance = Math.hypot(putter.bot.entity.position.x-basket.x, putter.bot.entity.position.z-basket.z)
  assert.ok(distance >= 10 && distance <= 15, 'the approach leaves a long putt at '+distance.toFixed(2)+' blocks')
  await command(putter, 'shot stance putt', /putt stance/)
  const putt = puttShot(putter.bot)
  assert.ok(putt, 'the lie has a playable putting line')
  const beforePutt = putter.messages.length, other = ace.messages.length
  await release(putter, putt, 0)
  await seeReplay(putter, beforePutt, putter, 'Long putt')
  assert.ok(onGround(ace), 'player who opted out stays on the ground')
  await movingCamera(putter)
  await until(() => leader.messages.includes('Round complete!'), 'long putt replay finishes the round')
  await restored()
  for (const state of players) {
    assert.equal(leader.messages.filter(line => line.startsWith(state.name + ' completed hole 1!')).length, 1)
    assert.equal(state.messages.filter(line => /^Round reward: \+\d+ credits\./.test(line)).length, 1)
  }
  assert.ok(!ace.messages.slice(other).some(line => line.startsWith('Instant replay:')))
  console.log('PASS: ' + distance.toFixed(1) + '-block putt replay respected opt-out and scored and paid exactly once')

  await round()
  const thrower = active(), watcher = players.find(state => state !== thrower)
  await command(watcher, 'camera on', /^Disc camera: on/)
  await lie(thrower)
  const before = leader.turns.length
  const flight = release(thrower, shortShot(thrower.bot, 55, .08))
  await until(() => attached(watcher), 'watcher enters second round live camera')
  await command(watcher, 'quit', /forfeited/)
  await restored([watcher])
  await flight
  await until(() => onGround(thrower) && leader.turns.length > before, 'the ongoing throw settles after the watcher leaves')
  await command(thrower, 'quit', /forfeited/)
  await restored()
  console.log('PASS: forfeiting during a live camera restored the viewer and allowed the other throw to resolve')
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  for (const { bot } of players) bot.quit()
  await pause(600)
  process.exit(process.exitCode || 0)
})
