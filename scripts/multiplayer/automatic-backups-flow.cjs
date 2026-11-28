const assert = require('node:assert/strict')
const { existsSync, readFileSync, readdirSync, unlinkSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')
const { command, connect, pause, until } = require('./parkland-41.cjs')

const data = 'plugins/NormalDiscGolf'
const directory = join(data, 'backups')
const fixture = JSON.parse(readFileSync('course-records-restart.json', 'utf8'))
const restore = JSON.parse(readFileSync('restore-report.json', 'utf8'))
const players = fixture.names.map(connect)
const [admin, guest] = players
const report = 'automatic-backup-report.json'
const ignored = join(directory, 'keep-me.zip')
const invalid = join(data, 'obstacles', 'bad+name.schem')
const automatic = /^ndg-auto-\d{8}-\d{6}-[0-9a-f]{8}\.zip$/
const names = () => readdirSync(directory).sort()
const autos = () => names().filter(name => automatic.test(name))
const log = () => readFileSync('logs/latest.log', 'utf8')

async function setting(key, value, response = /Saved backups\.|already set/) {
  await command(admin, 'admin set backups.' + key + ' ' + value, response)
}

async function apply() {
  await command(admin, 'admin apply', /Saved settings are now active/)
}

async function preview(archive) {
  const from = admin.messages.length
  await command(admin, 'admin backup preview ' + archive, /^Replace \d+ .* Add \d+/)
  return admin.messages.slice(from)
}

async function scheduleAndPrune() {
  assert.deepEqual(autos(), [])
  const manual = names().filter(name => /^ndg-\d/.test(name))
  assert.ok(manual.includes(restore.safety))
  writeFileSync(ignored, 'An unrelated file in the archive directory.')
  await command(guest, 'admin backup status', /do not have permission/)
  await command(admin, 'admin backup status', /Automatic backups .* Disabled/)
  await setting('keep', 0, /Use a value from 1/)
  await setting('interval-seconds', 4)
  await setting('keep', 2)
  await setting('enabled', true)
  await apply()
  await command(admin, 'admin backup status', /Next backup due/)
  await until(() => autos().length === 2, 'two scheduled ZIPs have been created', 30000)
  const original = autos()

  try {
    writeFileSync(invalid, 'Force snapshot validation to fail.')
    await setting('keep', 1)
    await apply()
    await until(() => /Automatic backup failed/.test(log()), 'a failed automatic backup is logged', 20000)
    assert.deepEqual(autos(), original, 'failed backup leaves the older ZIPs in place')
  } finally {
    if (existsSync(invalid)) unlinkSync(invalid)
  }
  await until(() => autos().length === 1 && !original.includes(autos()[0]), 'the next success prunes the old ZIPs', 20000)
  const protectedArchive = autos()[0]
  await until(() => log().includes('Removed 2 older automatic backup(s).'), 'retention cleanup is logged')
  await setting('enabled', false)
  await apply()
  await pause(5000)
  assert.deepEqual(autos(), [protectedArchive])

  const profile = readFileSync(restore.profile, 'utf8')
  const rating = Number(profile.match(/^  rating: (\d+)$/m)[1])
  await command(admin, 'profile rating ' + admin.name + ' ' + (rating + 1), /Set rating to/)
  await command(admin, 'admin backup preview ' + protectedArchive, /^\/ndg admin backup restore /)
  const confirm = admin.messages.findLast(line => /^\/ndg admin backup restore /.test(line))
  await command(admin, confirm.replace(/^\/ndg /, ''), /Restore scheduled for next restart/)
  await setting('enabled', true)
  await apply()
  await until(() => autos().length === 2, 'retention keeps the scheduled restore source', 20000)
  await pause(400)
  assert.ok(autos().includes(protectedArchive))
  await command(admin, 'admin backup cancel', /Scheduled restore canceled/)
  await until(() => autos().length === 1 && !autos().includes(protectedArchive), 'later cleanup prunes the canceled source', 20000)
  await pause(400)
  await preview(autos()[0])
  assert.deepEqual(names().filter(name => /^ndg-\d/.test(name)), manual)
  assert.equal(readFileSync(ignored, 'utf8'), 'An unrelated file in the archive directory.')

  await setting('keep', 2)
  await apply()
  writeFileSync(report, JSON.stringify({ manual, lastAuto: autos()[0] }))
  console.log('PASS: live configuration, scheduled ZIP files, retention, failure guard, disable, and pending restore protection.')
}

async function checkRestart() {
  const previous = JSON.parse(readFileSync(report, 'utf8'))
  await command(admin, 'admin backup status', /Interval 4 seconds .* Keep 2 automatic backups/)
  await until(() => autos().length === 2 && autos().some(name => name !== previous.lastAuto),
    'automatic backups continue after restart', 20000)
  assert.deepEqual(names().filter(name => /^ndg-\d/.test(name)), previous.manual)
  assert.ok(existsSync(ignored))
  await pause(400)
  await preview(autos().at(-1))
  console.log('PASS: schedule persisted across restart, new ZIP verified, manual and pre-restore files retained.')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients joined', 30000)
  if (process.argv.includes('--after-restart')) await checkRestart()
  else await scheduleAndPrune()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const player of players) player.bot.quit()
  await pause(500)
  process.exit(process.exitCode || 0)
})
