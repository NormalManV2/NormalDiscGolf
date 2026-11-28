const assert = require('node:assert/strict')
const { existsSync, readFileSync, readdirSync, unlinkSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')
const { basketShot, command, connect, inventory, pause, release, tee, until } = require('./parkland-41.cjs')

const data = 'plugins/NormalDiscGolf'
const archives = join(data, 'backups')
const pending = join(data, 'restore-pending.properties')
const reportFile = 'restore-report.json'
const saved = JSON.parse(readFileSync('course-records-restart.json', 'utf8'))
const players = saved.names.map(connect)
const [admin, guest] = players
const afterRestart = process.argv.includes('--after-restart')
const currentOnly = join(data, 'obstacles', 'restore-test-current-only.schem')

async function received(state, text, pattern) {
  const start = state.messages.length
  await command(state, text, pattern)
  return state.messages.slice(start).find(line => pattern.test(line))
}

async function confirmation(archive) {
  const line = await received(admin, 'admin backup preview ' + archive, /^\/ndg admin backup restore /)
  return line.replace(/^\/ndg /, '')
}

async function stageRestore() {
  const uuid = admin.bot.players[admin.name].uuid
  const profile = join(data, 'player-profiles', uuid + '.yml')
  const recipe = join(data, 'course-library', uuid, saved.id + '.yml')
  const originalRecipe = readFileSync(recipe, 'utf8')
  const created = await received(admin, 'admin backup create', /^Backup created .* ndg-[\w-]+\.zip$/)
  const archive = created.split(' ').at(-1)
  const originalRating = Number(readFileSync(profile, 'utf8').match(/^  rating: (\d+)$/m)[1])
  const changedRating = originalRating + 111
  await command(admin, 'profile rating ' + admin.name + ' ' + changedRating, /Set rating to/)
  unlinkSync(recipe)
  writeFileSync(currentOnly, readFileSync(join(data, 'obstacles', 'tree.schem')))

  const stale = await confirmation(archive)
  await command(admin, 'profile rating ' + admin.name + ' ' + (changedRating + 1), /Set rating to/)
  await command(admin, stale, /changes differ from the reviewed preview/)
  assert.equal(existsSync(pending), false)
  await command(admin, 'profile rating ' + admin.name + ' ' + changedRating, /Set rating to/)

  const mark = admin.messages.length
  const confirm = await confirmation(archive)
  const lines = admin.messages.slice(mark)
  assert.ok(lines.some(line => /replace.*player-profiles/.test(line)))
  assert.ok(lines.some(line => /add.*course-library/.test(line)))
  assert.ok(lines.some(line => /keep current.*restore-test-current-only/.test(line)))
  await command(guest, confirm, /do not have permission/)
  await command(admin, confirm.replace(/ [\da-f]{8}$/, ' invalid'), /Open a fresh restore preview/)
  assert.equal(existsSync(pending), false)
  await command(admin, confirm, /Restore scheduled for next restart/)
  await command(admin, 'admin backup cancel', /Scheduled restore canceled/)
  assert.equal(existsSync(pending), false)
  await command(admin, await confirmation(archive), /Restore scheduled for next restart/)
  await command(admin, 'admin backup list', /Restore scheduled for next restart/)
  assert.equal(existsSync(pending), true)
  assert.equal(existsSync(recipe), false)
  assert.match(readFileSync(profile, 'utf8'), new RegExp('^  rating: ' + changedRating + '$', 'm'))
  writeFileSync(reportFile, JSON.stringify({archive, recipe, profile, originalRating, originalRecipe, changedRating,
    backups: readdirSync(archives).filter(name => name.endsWith('.zip'))}))
  console.log('PASS: preview and confirmation, stale and unauthorized requests rejected, cancel worked, live data preserved until restart.')
}

async function checkRestored() {
  const report = JSON.parse(readFileSync(reportFile, 'utf8'))
  assert.equal(existsSync(pending), false)
  assert.equal(existsSync(join(data, 'restore-in-progress.properties')), false)
  assert.equal(readFileSync(report.recipe, 'utf8'), report.originalRecipe)
  assert.match(readFileSync(report.profile, 'utf8'), new RegExp('^  rating: ' + report.originalRating + '$', 'm'))
  assert.ok(existsSync(currentOnly))
  const additions = readdirSync(archives).filter(name => name.endsWith('.zip') && !report.backups.includes(name))
  assert.equal(additions.length, 1)
  const safety = additions[0]
  const before = admin.messages.length
  await command(admin, 'admin backup preview ' + safety, /^\/ndg admin backup restore /)
  assert.ok(admin.messages.slice(before).some(line => /replace.*player-profiles/.test(line)))
  writeFileSync(reportFile, JSON.stringify({...report, safety}))
  await command(admin, 'profile', new RegExp('Rating:.*' + report.originalRating))
  await command(admin, 'admin upgrade check ' + report.archive, /Matches backup layout/)

  await command(admin, 'party invite ' + guest.name, /Party invite sent/)
  await command(guest, 'party accept', /joined the party/)
  await command(admin, 'library play ' + saved.id, /Your party queued/)
  await until(() => players.every(state => state.bot.game.gameMode === 'adventure') && admin.turns.length > 0,
    'the restored course opens for both players', 120000)
  for (const state of players) {
    await command(state, 'status', /^Parkland.*Seed 41.*Course Library/)
    await command(state, 'library ghost compare', /Course ghost scorecard/)
    await command(state, 'shot timing on', /timing on/)
  }
  const scored = new Set()
  while (scored.size < players.length) {
    const active = () => players.find(state => state.name === admin.turns.at(-1))
    await until(() => active() && !scored.has(active()), 'unscored player is up')
    const state = active()
    const mark = state.messages.length
    await command(state, 'lie')
    await pause(250)
    assert.ok(state.bot.entity.position.distanceTo(tee) < .5)
    const shot = await basketShot(state.bot)
    assert.ok(shot)
    await release(state, shot)
    await until(() => state.messages.slice(mark).some(line => line.startsWith(state.name + ' completed hole 1!')), 'player holes out')
    scored.add(state)
  }
  await until(() => players.every(state => state.messages.includes('Round complete!')
    && state.bot.game.gameMode === state.original.mode), 'both finish and return to the lobby')
  for (const state of players) assert.deepEqual(inventory(state.bot), state.original.items)
  console.log('PASS: automatic safety ZIP, restored rating and saved layout, current-only files kept, and both players finished the course.')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients joined', 30000)
  if (afterRestart) await checkRestored()
  else await stageRestore()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  await pause(550)
  process.exit(process.exitCode || 0)
})
