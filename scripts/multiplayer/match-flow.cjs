const assert = require('node:assert/strict')
const { once } = require('node:events')
const { tee, until, pause, connect, inventory, command, clickModeJoin, basketShot, shortShot, release } = require('./parkland-41.cjs')

async function startMatch(players, party) {
  const [first, second] = players
  if (party) {
    await command(first, 'party invite ' + second.name, /invite/i)
    await command(second, 'party accept', /joined .*party/i)
    await command(first, 'queue match', /Your party has been queued for Match Play/)
  } else {
    const menu = once(first.bot, 'windowOpen')
    await command(first, 'menu')
    const [window] = await menu
    await until(() => window.slots[13]?.name === 'gold_ingot', 'Match Play in the picker')
    await clickModeJoin(first, 13)
    await until(() => first.messages.includes('You joined a Match Play round.'), 'Match Play queue membership')
    await pause(3800)
    assert.equal(first.bot.game.gameMode, first.original.mode, 'a lone entry waits for an opponent')
    await command(second, 'queue match-play', /You have been queued for Match Play/)
  }
  await until(() => players.every(state => state.messages.some(line => line.startsWith('Match Play:'))),
    'match to start', 120000)
  await readyHole(players, 1)
}

async function readyHole(players, hole) {
  await until(() => players.every(state => !state.error && state.bot.game.gameMode === 'adventure')
    && players[0].turns.length > 0, 'both clients out of the flyover', 15000)
  await pause(400)
  for (const state of players) await command(state, 'status', new RegExp('^Hole: ' + hole + '/3$'))
  if (hole === 1) assert.ok(active(players).bot.entity.position.distanceTo(tee) < 2, 'three-hole seed 41 fixture')
}

function active(players) {
  return players.find(state => state.name === players[0].turns.at(-1))
}

async function playOpeningHole(players, extraThrows) {
  for (const state of players) await command(state, 'shot timing on', /timing on/)
  const throws = new Map(players.map(state => [state,0]))
  const observer = players[0]
  for (let attempt=0; attempt<10 && !observer.messages.some(line => /^Hole 1: /.test(line)); attempt++) {
    const state = active(players)
    const before = observer.turns.length
    await command(state, 'lie')
    await pause(200)
    const shot = throws.get(state) < (state===observer ? extraThrows : 0)
      ? shortShot(state.bot) : await basketShot(state.bot)
    assert.ok(shot, state.name + ' has a scoring line from ' + state.bot.entity.position)
    await release(state,shot)
    throws.set(state,throws.get(state)+1)
    await until(() => observer.turns.length>before || observer.messages.some(line => /^Hole 1: /.test(line)),
      'next turn or completed hole')
  }
  assert.ok(players.every(state => observer.messages.some(line => line.startsWith(state.name + ' completed hole 1!'))),
    'both clients holed out with real throws')
  return throws
}

async function concede(players, loser, hole) {
  await readyHole(players,hole)
  const observer = players[0]
  if (active(players)!==loser) {
    await command(active(players), 'lie')
    await pause(200)
    await release(active(players), shortShot(active(players).bot))
    await until(() => active(players)===loser, loser.name + ' gets the turn')
  }
  await command(loser,'concede',new RegExp('^Hole '+hole+': .*wins \\(Hole conceded\\)\\.$'))
}

async function verifyFinish(players, result) {
  await until(() => players.every(state => state.messages.includes('Round complete!') &&
    state.messages.includes('Match result: '+result)), 'same final result for both clients')
  await pause(500)
  for (const {bot,original} of players) {
    assert.equal(bot.game.gameMode, original.mode, 'game mode restored')
    assert.ok(bot.entity.position.distanceTo(original.position)<2, 'lobby position restored')
    assert.deepEqual(inventory(bot), original.items, 'inventory restored')
  }
}

async function run(scenario, play) {
  const suffix = process.pid % 10000
  const players = ['A','B'].map(side => connect('MP'+scenario+side+suffix))
  try {
    await until(() => players.every(state => state.spawned), 'test clients connect')
    await pause(500)
    await play(players)
    console.log('PASS: '+scenario)
  } finally {
    for (const state of players) state.bot.quit()
    await pause(750)
  }
}

async function main() {
  await run('Draw', async players => {
    await startMatch(players,false)
    const opener = active(players)
    await command(players.find(state => state!==opener),'concede',/^Concede on your team's turn\.$/)
    const strokes = await playOpeningHole(players,0)
    const [first,second] = players
    assert.equal(strokes.get(first),strokes.get(second),'both players used the same number of strokes')
    const count = strokes.get(first)
    assert.ok(first.messages.includes('Hole 1: Halved ('+count+(count===1 ? ' stroke' : ' strokes')+' each).'))
    await readyHole(players,2)
    assert.equal(active(players),opener,'halved hole preserves tee honor')
    await command(first,'score',/^Match score: All square$/)
    await concede(players,first,2)
    await readyHole(players,3)
    assert.equal(active(players),second,'the winner of hole 2 has the next tee')
    await concede(players,second,3)
    await verifyFinish(players,'Draw (all square).')
    assert.ok(players.every(state => state.messages.some(line => line.startsWith('Round reward: +'))),
      'both players earn credits for their baskets')
  })
  await run('Clinch', async players => {
    await startMatch(players,true)
    const [first,second] = players
    const strokes = await playOpeningHole(players,2)
    assert.ok(strokes.get(first)>strokes.get(second),'lower stroke total wins the first hole')
    assert.ok(first.messages.includes('Hole 1: '+second.name+' wins ('+strokes.get(second)+'–'+strokes.get(first)+' strokes).'))
    await readyHole(players,2)
    assert.equal(active(players),second,'the lower scorer opens the next hole')
    await command(first,'score',new RegExp('^Match score: '+second.name+' 1 up$'))
    await concede(players,first,2)
    await verifyFinish(players,second.name+' wins 2 and 1.')
    assert.ok(!first.messages.some(line => /^Hole 3: /.test(line)),'clinched match skips the last hole')
    assert.ok(players.every(state => state.messages.some(line => line.startsWith('Round reward: +'))),
      'the early finish pays both earned basket credits')
  })
  await run('Quit', async players => {
    await startMatch(players,true)
    await command(players[0],'quit')
    await verifyFinish(players,players[1].name+' wins by forfeit.')
  })
}

main().catch(error => { console.error(error); process.exitCode = 1 }).finally(() => {
  setTimeout(() => process.exit(process.exitCode || 0),500)
})
