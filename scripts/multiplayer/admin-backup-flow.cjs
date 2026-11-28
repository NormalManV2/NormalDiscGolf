const assert = require('node:assert/strict')
const { once } = require('node:events')
const { existsSync, readFileSync, renameSync, unlinkSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')
const { basket, basketShot, command, connect, inventory, pause, release, tee, until } = require('./parkland-41.cjs')

const data = 'plugins/NormalDiscGolf'
const saved = JSON.parse(readFileSync('course-records-restart.json', 'utf8'))
const players = saved.names.map(connect)
const [admin, friend] = players
const afterUpgrade = process.argv.includes('--after-upgrade')
const report = 'backup-upgrade-report.json'
const itemText = item => JSON.stringify(item?.components ?? [])

async function open(action) {
  const opening = once(admin.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await action()
  return (await opening)[0]
}

async function backup() {
  await command(friend, 'admin backup create', /do not have permission/)
  const settings = await open(() => command(admin, 'admin'))
  await until(() => itemText(settings.slots[33]).includes('Backups and Upgrade Checks'), 'backup menu in server settings')
  const menu = await open(() => admin.bot.clickWindow(33, 0, 0))
  await until(() => itemText(menu.slots[11]).includes('Create Backup'), 'backup creation offered')
  const pattern = /^Backup created .* (ndg-[\w-]+\.zip)$/
  await admin.bot.clickWindow(11, 0, 0)
  await until(() => admin.messages.some(line => pattern.test(line)), 'backup is created')
  const filename = admin.messages.find(line => pattern.test(line)).match(pattern)[1]
  assert.ok(existsSync(join(data, 'backups', filename)))
  await until(() => admin.messages.some(line => /Course baselines [1-9]\d* .* Courses to review 0/.test(line)), 'verified course baselines')
  await command(admin, 'admin backup list', new RegExp(filename.replace('.', '\\.')))
  await command(admin, 'admin backup preview ' + filename, /^Replace 0 .* Add 0 .* Keep current 0 .* Identical [1-9]/)
  await command(admin, 'admin upgrade check ' + filename, /Matches backup layout/)
  const version = admin.messages.find(line => line.startsWith('Backup baseline')).split('Build ').at(-1)
  writeFileSync(report, JSON.stringify({filename, version}))
  console.log('PASS: admin-only ZIP backup, compatible generated layout baseline, and read-only identical preview.')
}

async function restorePreview(filename) {
  const config = join(data, 'config.yml')
  const oldObstacle = join(data, 'obstacles', 'tree.schem')
  const stagedObstacle = join(data, 'test-hold.schem')
  const newObstacle = join(data, 'obstacles', 'test-current-only.schem')
  const configBytes = readFileSync(config)
  const changed = Buffer.concat([configBytes, Buffer.from('\n# Test restore preview\n')])
  renameSync(oldObstacle, stagedObstacle)
  try {
    writeFileSync(config, changed)
    writeFileSync(newObstacle, 'local-only fixture')
    const mark = admin.messages.length
    await command(admin, 'admin backup preview ' + filename, /^Replace 1 .* Add 1 .* Keep current 1 .* Identical [1-9]/)
    await until(() => admin.messages.slice(mark).some(line => /keep current.*obstacles\/test-current-only.schem/.test(line)), 'retained file is listed')
    const lines = admin.messages.slice(mark)
    assert.ok(lines.some(line => /replace.*config.yml/.test(line)))
    assert.ok(lines.some(line => /add.*obstacles\/tree.schem/.test(line)))
    assert.deepEqual(readFileSync(config), changed)
    assert.equal(existsSync(oldObstacle), false)
    assert.equal(readFileSync(newObstacle, 'utf8'), 'local-only fixture')
  } finally {
    writeFileSync(config, configBytes)
    if (existsSync(newObstacle)) unlinkSync(newObstacle)
    renameSync(stagedObstacle, oldObstacle)
  }
}

async function upgraded() {
  const { filename, version } = JSON.parse(readFileSync(report, 'utf8'))
  const mark = admin.messages.length
  await command(admin, 'admin upgrade check ' + filename, /Matches backup layout/)
  const reportLines = admin.messages.slice(mark)
  assert.ok(reportLines.some(line => line.endsWith('Build ' + version) && line.startsWith('Backup baseline')))
  assert.ok(reportLines.some(line => line.startsWith('Generated course upgrade check') && !line.endsWith('Build ' + version)))
  await restorePreview(filename)

  const uuid = admin.bot.players[admin.name].uuid
  const recipe = join(data, 'course-library', uuid, saved.id + '.yml')
  const original = readFileSync(recipe, 'utf8')
  try {
    const incompatible = original.replace(/^generator: \d+$/m, 'generator: 9999')
    assert.notEqual(incompatible, original)
    writeFileSync(recipe, incompatible)
    await command(admin, 'admin upgrade check', /Unsupported course file or generator version/)
  } finally {
    writeFileSync(recipe, original)
  }

  await command(admin, 'party invite ' + friend.name, /Party invite sent/)
  await command(friend, 'party accept', /joined the party/)
  await command(admin, 'library play ' + saved.id, /Your party queued/)
  await until(() => players.every(state => state.bot.game.gameMode === 'adventure') && admin.turns.length > 0,
    'the upgraded saved course opens for both players', 120000)
  for (const state of players) {
    await command(state, 'status', /^Parkland.*Seed 41.*Course Library/)
    await command(state, 'library ghost compare', /Course ghost scorecard/)
    await command(state, 'shot timing on', /timing on/)
  }
  const scored = new Set()
  while (scored.size < players.length) {
    const getActive = () => players.find(state => state.name === admin.turns.at(-1))
    await until(() => getActive() && !scored.has(getActive()), 'unscored player is up')
    const state = getActive()
    const from = state.messages.length
    await command(state, 'lie')
    await pause(250)
    assert.ok(state.bot.entity.position.distanceTo(tee) < .5, 'the saved tee is in its original place')
    const shot = await basketShot(state.bot, 'driver', basket)
    assert.ok(shot, 'the original basket is playable')
    await release(state, shot)
    await until(() => state.messages.slice(from).some(line => line.startsWith(state.name + ' completed hole 1!')), 'player holes out')
    scored.add(state)
  }
  await until(() => players.every(state => state.messages.includes('Round complete!')
    && state.bot.game.gameMode === state.original.mode), 'both finish and return to the lobby')
  for (const state of players) assert.deepEqual(inventory(state.bot), state.original.items)
  console.log('PASS: cross-build layout match, read-only restore preview, incompatible generator detection, persisted ghosts, and two players finishing the saved layout.')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients joined', 30000)
  if (afterUpgrade) await upgraded()
  else await backup()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  await pause(550)
  process.exit(process.exitCode || 0)
})
