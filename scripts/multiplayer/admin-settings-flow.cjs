const assert = require('node:assert/strict')
const { readFileSync, writeFileSync } = require('node:fs')
const { once } = require('node:events')
const { resolve } = require('node:path')
const { command, connect, pause, until } = require('./parkland-41.cjs')

const admin = connect('Config_Admin')
const player = connect('Config_Guest')
const players = [admin, player]
const afterRestart = process.argv.includes('--after-restart')
const config = resolve('plugins/NormalDiscGolf/config.yml')
const backup = config + '.bak'
const itemText = item => JSON.stringify(item?.components ?? [])

async function open(state, action) {
  const opened = once(state.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await action()
  return (await opened)[0]
}

async function set(key, value) {
  await command(admin, 'admin set ' + key + ' ' + value, /Saved .* to config\.yml\./)
}

async function freshInstall() {
  await command(admin, 'admin status', /Resource pack .* Local clients only/)
  assert.ok(admin.messages.some(line => /Clubhouse .* Ready/.test(line)))
  await command(admin, 'admin check', /Config check passed/)
  const pack = await fetch('http://127.0.0.1:8088/ndg/pack.zip', { signal: AbortSignal.timeout(5000) })
  assert.equal(pack.status, 200)
  assert.deepEqual(Buffer.from(await pack.arrayBuffer()), readFileSync(resolve('plugins/NormalDiscGolf/NormalDiscGolfPack.zip')))

  const original = readFileSync(config)
  await command(player, 'admin set course.holes 1', /do not have permission/)
  assert.deepEqual(readFileSync(config), original, 'an ordinary player cannot edit the file')
  const menu = await open(admin, () => command(admin, 'admin'))
  await until(() => itemText(menu.slots[21]).includes('Courses'), 'operator can browse settings categories')
  await admin.bot.clickWindow(21, 0, 0)
  const holes = () => menu.slots.findIndex((item, index) => index >= 9 && index < 45 && itemText(item).includes('course.holes'))
  await until(() => holes() >= 0, 'courses category lists the hole count')
  await admin.bot.clickWindow(holes(), 0, 0)
  await until(() => admin.messages.includes('[Edit course.holes]'), 'setting offers a typed command prompt')
  await command(admin, 'admin set course.holes 19', /Use a value from 1 to 18/)
  assert.deepEqual(readFileSync(config), original, 'invalid edits leave the file unchanged')
  await set('course.holes', 1)
  assert.deepEqual(readFileSync(backup), original, 'a successful edit backs up the exact previous file')
  assert.match(readFileSync(config, 'utf8'), /Random draws a fresh seed per course/, 'configuration comments are preserved')
  await command(admin, 'admin get course.holes', /Active 18 .* Saved 1/)

  await set('weekly-tournament.min-players', 7)
  await set('weekly-tournament.max-players', 4)
  await command(admin, 'admin apply', /Config check failed/)
  assert.ok(admin.messages.some(line => line.includes('Must be at most max-players.')))
  await command(admin, 'admin reset weekly-tournament.min-players', /Saved .* to config/)
  await command(admin, 'admin reset weekly-tournament.max-players', /Saved .* to config/)

  await set('course.profiles.pilot.terrain', 'desert')
  await set('course.profiles.pilot.seed-mode', 'fixed')
  await set('course.profiles.pilot.seed', 41)
  await set('course.profiles.pilot.terrains.desert.elevation', 0)
  await set('course.profile', 'pilot')
  await set('queue-countdown-seconds', 2)
  await set('preview.enabled', 'false')
  await set('resource-pack.enabled', 'false')
  await command(admin, 'admin apply', /Restart the server to apply them together/)
  await command(admin, 'admin get resource-pack.enabled', /Active true .* Saved false/)
  console.log('PASS: clean install, startup clubhouse and pack export/delivery, permissions, admin GUI, backups, validation, typed profiles, and restart-only settings.')
}

async function restarted() {
  await command(admin, 'admin status', /Resource pack .* Disabled/)
  await command(admin, 'admin get course.holes', /Active 1 .* Saved 1/)
  await command(admin, 'admin get course.profiles.pilot.terrain', /Active desert .* Saved desert/)
  const valid = readFileSync(config)
  try {
    writeFileSync(config, 'course: [\n')
    await command(admin, 'admin apply', /Could not read or write NormalDiscGolf settings/)
  } finally {
    writeFileSync(config, valid)
  }
  await command(admin, 'admin get course.profiles.pilot.terrain', /Active desert .* Saved desert/)

  await command(player, 'queue match new', /queued for Match Play/)
  await set('course.profiles.pilot.terrain', 'mountain')
  await command(admin, 'admin apply', /no active, queued, or preparing rounds/)
  await command(admin, 'admin get course.profiles.pilot.terrain', /Active desert .* Saved mountain/)
  await command(player, 'leave', /left the disc golf queue/)
  const mark = admin.messages.length
  for (let attempt = 0; attempt < 10 && !admin.messages.slice(mark).includes('Saved settings are now active.'); attempt++) {
    await command(admin, 'admin apply', /Saved settings are now active|no active, queued, or preparing rounds/)
    await pause(250)
  }
  assert.ok(admin.messages.slice(mark).includes('Saved settings are now active.'))
  await command(admin, 'admin get course.profiles.pilot.terrain', /Active mountain .* Saved mountain/)
  await command(admin, 'party invite ' + player.name, /Party invite sent/)
  await command(player, 'party accept', /joined the party/)
  await command(admin, 'queue ffa new', /queued for FFA/)
  await until(() => players.every(state => state.bot.game.gameMode === 'adventure') && admin.turns.length > 0,
    'both players start a round with the new config', 120000)
  for (const state of players) {
    await command(state, 'status', /^Mountain.*Seed 41/)
    assert.ok(state.messages.includes('Hole: 1/1'))
  }
  const active = () => players.find(state => state.name === admin.turns.at(-1))
  const first = active()
  await command(first, 'concede', /Hole conceded/)
  await until(() => active() !== first, 'next player takes their turn')
  await command(active(), 'concede', /Hole conceded/)
  await until(() => players.every(state => state.messages.includes('Round complete!')), 'the configured round completes')
  console.log('PASS: restart applies startup settings, malformed YAML leaves active state intact, queued games block apply, and the next two-player round uses the live configuration.')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients join', 30000)
  if (afterRestart) await restarted()
  else await freshInstall()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
