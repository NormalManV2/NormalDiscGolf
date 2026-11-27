const assert = require('node:assert/strict')
const { once } = require('node:events')
const { command, connect, openCourseLobbies, until } = require('./parkland-41.cjs')

async function browseMatch(player) {
  const opened = once(player.bot, 'windowOpen', { signal: AbortSignal.timeout(10000) })
  await command(player, 'menu')
  const [picker] = await opened
  await until(() => picker.slots[13]?.name === 'gold_ingot', 'Match Play in the picker')
  return openCourseLobbies(player, 13)
}

function room(window, item) {
  return window.slots.slice(0, 36).findIndex(slot => slot?.name === item)
}

async function main() {
  const players = ['Lobby_Alice', 'Lobby_Bob', 'Lobby_Casey'].map(connect)
  const [alice, bob, casey] = players
  try {
    await until(() => players.every(player => player.spawned), 'three clients in the lobby')
    const list = await browseMatch(casey)
    await until(() => list.slots[13]?.name === 'paper', 'empty lobby list')

    await browseMatch(alice)
    await alice.bot.clickWindow(36, 0, 0)
    await until(() => alice.messages.some(line => line.startsWith('Preparing course.')), 'course generation begins with one queued player')
    assert.equal(alice.bot.game.gameMode, alice.original.mode, 'Match Play waits for an opponent')

    await browseMatch(bob)
    await bob.bot.clickWindow(37, 0, 0)
    await until(() => room(list, 'spruce_sapling') >= 0 && room(list, 'red_sand') >= 0, 'both courses appear without reopening')
    const woodland = list.slots[room(list, 'spruce_sapling')]
    const desert = list.slots[room(list, 'red_sand')]
    assert.match(JSON.stringify(woodland.components), /Lobby_Alice/)
    assert.match(JSON.stringify(desert.components), /Lobby_Bob/)
    assert.match(JSON.stringify(desert.components), /1 \/ 2 queued/)

    await casey.bot.clickWindow(room(list, 'red_sand'), 0, 0)
    await until(() => [bob, casey].every(player => player.messages.some(line => line.startsWith('Match Play:'))),
      'players in the selected desert lobby start their match', 90000)
    assert.equal(alice.bot.game.gameMode, alice.original.mode, 'the woodland lobby is a separate queue')

    const leaving = await browseMatch(alice)
    await until(() => leaving.slots[47]?.name === 'barrier', 'queue leave button')
    await alice.bot.clickWindow(47, 0, 0)
    await until(() => leaving.slots[13]?.name === 'paper', 'empty lobby is removed immediately')
    await command(alice, 'status', /not in a round or queue/)
    console.log('PASS: live lobby roster, terrain choices, preparation before matchmaking, exact lobby join, and queue cleanup.')
  } finally {
    for (const player of players) player.bot.quit()
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 })
