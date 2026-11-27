const assert = require('node:assert/strict')
const { until, pause, connect, inventory, command, shortShot, release } = require('./parkland-41.cjs')

const terrain = process.env.NDG_TEST_TERRAIN || 'Sky'
const requiredLayouts = new Set(['Air Shot','Dogleg','Elevated Basket'])
const active = players => players.find(state=>state.name===players[0].turns.at(-1))
const done = state => state.messages.includes('Round complete!')

async function playCourse(sequence) {
  const players = ['A','B'].map(side=>connect('Terrain'+sequence+side+process.pid%10000))
  const [observer, second] = players
  try {
    await until(()=>players.every(state=>state.spawned),'two clients enter the lobby')
    await command(observer,'party invite '+second.name,/Party invite sent/)
    await command(second,'party accept',/joined the party/)
    await command(observer,'queue closest-to-pin',/Your party has been queued/)
    await until(()=>players.every(state=>state.messages.some(line=>line.startsWith(terrain+' • Seed '))),
      'the course profile and seed are announced',120000)
    const details = players.map(state=>state.messages.find(line=>line.startsWith(terrain+' • Seed ')))
    assert.equal(details[0],details[1],'the whole round shares its generated course')
    const metadata = details[0].match(/Seed (-?\d+) • Difficulty (\d+)\/100/)
    assert.ok(metadata && Number(metadata[2])>=1 && Number(metadata[2])<=100)
    const layouts = new Set()
    const tees = []
    for (const state of players) await command(state,'shot timing on',/timing on/)

    for (let hole=1;hole<=3;hole++) {
      await until(()=>active(players) && players.every(state=>state.bot.game.gameMode==='adventure'),
        'players return from the flyover')
      await pause(350)
      const beforeStatus = observer.messages.length
      await command(observer,'status',new RegExp('^Hole: '+hole+'/3$'))
      await until(()=>observer.messages.slice(beforeStatus).some(line=>line.startsWith('Layout: ')),
        'the current hole layout appears in status')
      layouts.add(observer.messages.slice(beforeStatus).find(line=>line.startsWith('Layout: ')).slice(8))
      const used = new Set()
      for (let shot=0;shot<2;shot++) {
        await until(()=>active(players) && !used.has(active(players)),'a player gets their one throw')
        const state = active(players)
        used.add(state)
        await command(state,'lie')
        await pause(260)
        const start = state.bot.entity.position.clone()
        if (shot===0) tees.push(start.toString())
        assert.ok(state.bot.blockAt(start.offset(0,-1,0))?.name==='smooth_stone','tee pad supports the thrower')
        if (terrain==='Sky') assert.ok(start.y>115,'Sky tee sits on an elevated platform')
        const before = observer.messages.length, turn = observer.turns.length
        await release(state,shortShot(state.bot,20,-.6,start.offset(10,0,0)))
        await until(()=>observer.messages.slice(before).some(line=>line.startsWith(state.name+' finished hole '+hole+' at ')),
          'the real throw lands on the themed course')
        await until(()=>observer.turns.length>turn || done(observer),'the next turn or result arrives')
      }
    }
    assert.deepEqual(layouts,requiredLayouts,'the per-terrain archetype list is honored')
    await until(()=>players.every(done),'both players receive the final standings')
    await pause(450)
    for (const state of players) {
      assert.ok(state.messages.includes('Final standings:'))
      assert.equal(state.bot.game.gameMode,state.original.mode)
      assert.ok(state.bot.entity.position.distanceTo(state.original.position)<2)
      assert.deepEqual(inventory(state.bot),state.original.items)
    }
    console.log('PASS: '+terrain+' seed '+metadata[1]+' completed with two clients, '+[...layouts].join(', ')+' and lobby restoration')
    return {seed:metadata[1],tees}
  } finally {
    for (const {bot} of players) bot.quit()
    await pause(700)
  }
}

async function main() {
  const first = await playCourse(1)
  const second = await playCourse(2)
  assert.notEqual(first.seed,second.seed,'the same selected terrain gets a fresh seed')
  assert.notDeepEqual(first.tees,second.tees,'the second course changes the positions of the tees')
  console.log('PASS: two queued rounds in the same environment had distinct seeds and tee locations')
}

main().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>process.exit(process.exitCode||0))
