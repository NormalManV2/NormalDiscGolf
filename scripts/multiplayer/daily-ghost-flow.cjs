const assert = require('node:assert/strict')
const fs = require('node:fs')
const { basketShot, command, connect, inventory, pause, release, shortShot, until } = require('./parkland-41.cjs')

const report = process.env.NDG_GHOST_TEST_REPORT || 'build/daily-ghost-restart.json'
const restarted = process.argv.includes('--after-restart')
const names = restarted ? JSON.parse(fs.readFileSync(report, 'utf8')).names : ['GhostA', 'GhostB'].map(name => name+process.pid%10000)
const players = names.map(connect)
const [leader, partner] = players
const recorded = new Map()
const displayed = new Map()
const active = () => players.find(state => state.name === leader.turns.at(-1))

for (const state of players) {
  recorded.set(state, [[], []])
  displayed.set(state, [])
  state.bot._client.on('world_particles', packet => {
    const point = [packet.x, packet.y, packet.z]
    if (packet.particle.type === 'end_rod' && state.capturing != null) recorded.get(state)[state.capturing].push(point)
    if (packet.particle.type === 'dust' && (packet.particle.data.color & 0xffffff) === 0xa88bff) displayed.get(state).push(point)
  })
}

async function round() {
  const marks = players.map(state => state.messages.length)
  const turns = leader.turns.length
  await command(leader, 'daily play', /Your party has been queued for Daily Course/)
  await until(() => leader.turns.length > turns && players.every(state => state.bot.game.gameMode === 'adventure'),
    'both clients arrive at the Daily tees', 120000)
  for (const state of players) {
    state.start = marks[players.indexOf(state)]
    await command(state, 'shot timing on', /timing on/)
  }
}

async function comparison(state, best, completed = []) {
  const from = state.messages.length
  await command(state, 'daily ghost compare', new RegExp('^Daily ghost scorecard .* Best ' + (best[0]+best[1]) + ' strokes$'))
  const lines = best.map((target, hole) => 'Hole '+(hole+1)+': You '+(completed[hole] ?? '—')+' | Best '+target)
  await until(() => lines.every(line=>state.messages.slice(from).some(message=>message.startsWith(line))),
    'the private scorecard has the expected target on each hole')
  return state.messages.slice(from)
}

async function showOnly(state, other, hole, compareFrames) {
  const from = displayed.get(state).length, otherFrom = displayed.get(other).length
  await command(state, 'daily ghost on', /^Daily ghost paths are on\.$/)
  await until(() => displayed.get(state).length >= from+3, 'enabled player receives their saved flight samples')
  assert.equal(displayed.get(other).length, otherFrom, 'ghost paths are private and start disabled')
  if (compareFrames) {
    const frames = recorded.get(state)[hole-1]
    const points = [...new Map(displayed.get(state).slice(from).map(point => [point.join(','),point])).values()]
    const matching = points.filter(point => frames.some(frame =>
      Math.hypot(...point.map((coordinate, axis) => coordinate - frame[axis])) < .065))
    // The recording also includes the launch point, before the first live flight particle.
    assert.ok(matching.length >= Math.max(3, points.length-2), 'ghost points match the real recorded throws for this player and hole')
  }
  await command(state, 'daily ghost off', /^Daily ghost paths are off\.$/)
  const count = displayed.get(state).length
  await pause(1150)
  assert.equal(displayed.get(state).length, count, 'turning ghosts off stops the refresh')
}

async function ready(hole) {
  await until(() => active() && players.every(state => state.bot.game.gameMode === 'adventure'), 'flyover restores both players')
  for (const state of players) await command(state, 'status', new RegExp('^Hole: '+hole+'/2$'))
}

async function playHole(hole, extra, capture) {
  await ready(hole)
  const remaining = new Map(players.map((state,index) => [state,extra[index]]))
  const finished = new Set()
  while (finished.size < players.length) {
    await until(() => active() && !finished.has(active()), 'an unfinished player takes the next stroke')
    const state = active()
    const turns = leader.turns.length
    const mark = leader.messages.length
    await command(state,'lie')
    await pause(225)
    const cap = state.bot.registry.blocksByName.yellow_concrete.id
    let pin
    await until(() => (pin = state.bot.findBlock({matching:cap, maxDistance:65})), 'the current basket reaches the client')
    const target = pin.position.offset(.5, -1.1, .5)
    const miss = remaining.get(state) > 0
    const shot = miss ? shortShot(state.bot, 20, -1.48, target) : await basketShot(state.bot, 'driver', target)
    assert.ok(shot, 'there is a playable flight to '+target)
    if (capture) state.capturing = hole-1
    await release(state, shot)
    state.capturing = null
    if (miss) remaining.set(state, remaining.get(state)-1)
    else {
      await until(() => leader.messages.slice(mark).some(line => line.startsWith(state.name+' completed hole '+hole+'!')),
        state.name+' holes out')
      finished.add(state)
    }
    await until(() => leader.turns.length > turns || leader.messages.slice(mark).includes('Round complete!'), 'the next turn arrives')
  }
}

async function finishRound() {
  await until(() => players.every(state=>state.messages.slice(state.start).includes('Round complete!')), 'both complete the round')
  await until(() => players.every(({bot,original}) => bot.game.gameMode === original.mode), 'lobby game mode restored')
  for (const {bot,original} of players) assert.deepEqual(inventory(bot), original.items)
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients connect')
  await command(leader, 'daily ghost on', /^Daily ghosts are available while playing the Daily Course\.$/)
  await command(leader, 'party invite '+partner.name, /Party invite sent/)
  await command(partner, 'party accept', /joined the party/)
  await round()
  if (restarted) {
    await comparison(leader,[1,1])
    await comparison(partner,[1,2])
    await showOnly(leader, partner, 1, false)
    await command(leader, 'quit', /forfeited/)
    await command(partner, 'quit', /forfeited/)
    console.log('PASS: both personal bests and their per-hole scores survived a Paper restart and can be shown in a new round.')
    return
  }

  await command(leader, 'daily ghost on', /^Hole out on every hole to record your first Daily ghost\.$/)
  await playHole(1,[1,0],true)
  await playHole(2,[0,1],true)
  await finishRound()
  for (const state of players) assert.ok(state.messages.slice(state.start).some(line=>line.startsWith('Daily ghost saved.')))

  await round()
  await comparison(leader,[2,1])
  await comparison(partner,[1,2])
  await showOnly(leader, partner, 1, true)
  await showOnly(partner, leader, 1, true)
  await playHole(1,[0,2],false)
  await ready(2)
  assert.ok((await comparison(leader,[2,1],[1])).includes('Hole 1: You 1 | Best 2 | 1 stroke ahead'))
  assert.ok((await comparison(partner,[1,2],[3])).includes('Hole 1: You 3 | Best 1 | 2 strokes behind'))
  await showOnly(leader, partner, 2, true)
  await playHole(2,[0,0],false)
  await finishRound()
  assert.ok(leader.messages.slice(leader.start).includes('Through 2 holes: 1 stroke ahead'))
  assert.ok(partner.messages.slice(partner.start).includes('Through 2 holes: 1 stroke behind'))
  assert.ok(leader.messages.slice(leader.start).some(line=>line.startsWith('Daily ghost saved.')))
  assert.ok(!partner.messages.slice(partner.start).some(line=>line.startsWith('Daily ghost saved.')))
  fs.writeFileSync(report, JSON.stringify({names}))
  console.log('PASS: two multiplayer rounds, private per-hole paths, toggles, accurate stroke deltas, and lower-score replacement.')
}

main().catch(error=>{ console.error(error.stack); process.exitCode = 1 }).finally(()=>{
  for (const {bot} of players) bot.quit()
})
