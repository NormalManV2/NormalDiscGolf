const assert = require('node:assert/strict')
const fs = require('node:fs')
const { Vec3 } = require('vec3')
const { basket, tee, until, pause, connect, inventory, command, basketShot, shortShot, release } = require('./parkland-41.cjs')

const players = ['A', 'B'].map(side => connect('Stats' + side + process.pid % 10000))
const [leader, partner] = players
const active = () => players.find(state => state.name === leader.turns.at(-1))
const water = new Vec3(60, 67, 56)
const horizontal = (a, b) => Math.hypot(a.x-b.x, a.z-b.z)
const summaryKeys = ['Round summary - ', 'Score: ', 'Longest drive: ', 'Longest putt: ', 'Fairway hits: ', 'Putting accuracy: ', 'OB penalties: ']
const summary = messages => messages.filter(line => summaryKeys.some(key => line.startsWith(key)))

async function fixture() {
  const start = leader.messages.length
  await command(leader, 'status', /^Round: ndg_ffa_/)
  const world = leader.messages.slice(start).find(line => line.startsWith('Round: ')).slice(7)
  assert.match(world, /^ndg_ffa_junior_[a-f0-9-]+$/)
  const place = 'execute in minecraft:' + world + ' run setblock ' + [water.x,water.y,water.z].join(' ') + ' minecraft:water'
  const request = process.env.NDG_ANALYTICS_FIXTURE || 'build/analytics-water-request.json'
  fs.writeFileSync(request, JSON.stringify({world, command:place}))
  console.log('FIXTURE: ' + place)
  await until(() => players.every(({bot}) => bot.blockAt(water)?.name === 'water'), 'the fixture water reaches both clients', 90000)
}

async function lie(state) {
  await until(() => active() === state, 'the intended player gets the next stroke')
  await command(state, 'lie')
  await pause(240)
}

async function throwAndAdvance(state, shot, slot = 2) {
  const turns = leader.turns.length
  await release(state, shot, slot)
  await until(() => leader.turns.length > turns || leader.messages.includes('Round complete!'), 'the server advances from the resolved throw')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients connect')
  for (const state of players) await command(state, 'summary', /^Finish a round to see a round summary\.$/)
  await command(leader, 'party invite ' + partner.name, /Party invite sent/)
  await command(partner, 'party accept', /joined the party/)
  await command(leader, 'queue ffa', /queued for FFA/)
  await until(() => active() && players.every(({bot}) => bot.game.gameMode === 'adventure'), 'the round starts after the flyover', 120000)
  await pause(400)
  for (const state of players) {
    await command(state, 'shot timing on', /timing on/)
    await command(state, 'status', /^Hole: 1\/1$/)
  }
  await fixture()
  const putter = active()
  const penalized = players.find(state => state !== putter)

  await lie(putter)
  const driveOrigin = putter.bot.entity.position.clone()
  const approachAngle = Math.atan2(basket.z-tee.z, basket.x-tee.x) + .37
  await throwAndAdvance(putter, shortShot(putter.bot, 50, .12, tee.offset(10*Math.cos(approachAngle), 0, 10*Math.sin(approachAngle))))

  await lie(penalized)
  const beforeWater = penalized.messages.length
  await throwAndAdvance(penalized, shortShot(penalized.bot, 35, 0, tee.offset(10, 0, 0)))
  assert.ok(penalized.messages.slice(beforeWater).includes('Hazard or out of bounds. One penalty stroke. Replay the previous lie.'))

  await lie(putter)
  const landedLie = putter.bot.entity.position.clone()
  assert.ok(horizontal(landedLie,basket) < 15 && horizontal(landedLie,basket) > 4, 'the drive reaches the green without scoring')
  await command(putter, 'shot stance putt', /putt stance/)
  const beforeMiss = putter.messages.length
  const from = putter.bot.entity.position.clone()
  await throwAndAdvance(putter, shortShot(putter.bot, 25, -1.38, from.scaled(2).subtract(basket)), 0)
  assert.ok(!putter.messages.slice(beforeMiss).some(line => line.startsWith(putter.name + ' completed hole')))

  await lie(penalized)
  assert.ok(penalized.bot.entity.position.distanceTo(tee) < .5, 'the penalty returns the player to the tee for the next turn')
  const retry = await basketShot(penalized.bot)
  assert.ok(retry, 'an open scoring line exists around the water')
  await throwAndAdvance(penalized, retry)

  await lie(putter)
  const puttDistance = horizontal(putter.bot.entity.position, basket)
  const putt = await basketShot(putter.bot, 'putting')
  assert.ok(putt, 'a playable putting line')
  await throwAndAdvance(putter, putt, 0)
  await until(() => players.every(({messages}) => summary(messages).length >= summaryKeys.length), 'both receive a full post-round summary')
  const [putterReport, penaltyReport] = [putter,penalized].map(({messages}) => summary(messages))
  console.log(putterReport.join(' | '))
  console.log(penaltyReport.join(' | '))
  const common = ['Round summary - FFA | Parkland', 'Score: 0 (3 strokes, par 3) • 1/1 holes']
  assert.deepEqual(putterReport.slice(0, 2), common)
  assert.deepEqual(penaltyReport.slice(0, 2), common)
  const reportedDrive = Number(putterReport[2].match(/^Longest drive: (\d+\.\d) blocks$/)?.[1])
  assert.ok(Math.abs(reportedDrive-horizontal(driveOrigin,landedLie)) < 1, 'drive distance matches the networked landing')
  assert.deepEqual(putterReport.slice(3), [
    'Longest putt: ' + puttDistance.toFixed(1) + ' blocks',
    'Fairway hits: 1/1 (100%)', 'Putting accuracy: 1/2 (50%)', 'OB penalties: 0'
  ])
  assert.deepEqual(penaltyReport.slice(2), [
    'Longest drive: —', 'Longest putt: —', 'Fairway hits: 0/1 (0%)', 'Putting accuracy: — (no putts)', 'OB penalties: 1'
  ])
  console.log('PASS: real fairway drive, missed and made putts, and a water penalty produced separate correct summaries')

  await until(() => players.every(({bot,original}) => bot.game.gameMode === original.mode
    && bot.entity.position.distanceTo(original.position) < 2), 'both players return to the lobby')
  for (const state of players) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    const start = state.messages.length
    await command(state, 'summary text', /^OB penalties:/)
    assert.deepEqual(summary(state.messages.slice(start)), state === putter ? putterReport : penaltyReport)
  }
  console.log('PASS: /ndg summary text prints only the requesting player’s result after round cleanup')
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(async () => {
  for (const {bot} of players) bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
