const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { existsSync, readFileSync, readdirSync, unlinkSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')
const { basketShot, command, connect, pause, release, tee, until } = require('./parkland-41.cjs')

const directory = 'plugins/NormalDiscGolf/backups'
const remote = process.env.NDG_OFFSITE_TEST_DIR
const endpoint = process.env.NDG_OFFSITE_TEST_URL
const fixture = JSON.parse(readFileSync('course-records-restart.json', 'utf8'))
const known = JSON.parse(readFileSync('restore-report.json', 'utf8'))
const players = fixture.names.map(connect)
const [admin, guest] = players
const report = 'offsite-backup-report.json'
const autos = () => readdirSync(directory).filter(name => /^ndg-auto-/.test(name)).sort()
const log = () => readFileSync('logs/latest.log', 'utf8')

async function setting(key, value, response = /Saved backups\.|already set/) {
  await command(admin, 'admin set backups.' + key + ' ' + value, response)
}

async function saveAndRecover() {
  await command(guest, 'admin backup verify ' + known.archive, /do not have permission/)
  await setting('offsite.url', endpoint.replace('https:', 'http:'), /Use a private HTTPS/)
  await setting('offsite.url', endpoint)
  if (process.env.NDG_OFFSITE_TEST_AUTH_FILE) await setting('offsite.authorization-file', true)
  await command(admin, 'admin apply', /Saved settings are now active/)
  await command(guest, 'admin backup probe', /do not have permission/)
  if (process.env.NDG_OFFSITE_TEST_AUTH_FILE) {
    const authFile = 'plugins/NormalDiscGolf/offsite-authorization.txt'
    const credentials = readFileSync(authFile)
    const before = readdirSync(remote)
    try {
      writeFileSync(authFile, 'fixture-user@example.test\n\nfixture-password')
      await command(admin, 'admin backup probe', /username on line 1 and the app password on line 2/)
      assert.deepEqual(readdirSync(remote), before)
    } finally {
      writeFileSync(authFile, credentials)
    }
  }
  const probeStart = admin.messages.length
  await command(admin, 'admin backup probe', /^Synthetic files created: /)
  const probeReport = admin.messages.slice(probeStart)
  assert.ok(probeReport.some(line => /Off-server probe passed/.test(line)))
  const probe = probeReport.find(line => /^Synthetic files created: /.test(line)).split(' ')[3]
  const probeBytes = readFileSync(join(remote, probe))
  assert.equal(readFileSync(join(remote, probe + '.sha256'), 'utf8').trim(),
    probeBytes.length + ':' + createHash('sha256').update(probeBytes).digest('hex'))
  if (process.env.NDG_OFFSITE_TEST_UNSAFE_PUT) {
    const flag = join(remote, 'ignore-precondition')
    writeFileSync(flag, '')
    try {
      await command(admin, 'admin backup probe', /destination accepted an overwrite/)
    } finally {
      unlinkSync(flag)
    }
  }
  await setting('interval-seconds', 4)
  await setting('keep', 1)
  await setting('enabled', true)
  await command(admin, 'admin apply', /Saved settings are now active/)
  await command(admin, 'admin backup status', /Off-server copies .* Configured/)
  await until(() => /Off-server backup verified: ndg-auto-/.test(log()), 'a scheduled ZIP is uploaded', 30000)
  const first = autos()[0]
  assert.ok(existsSync(join(remote, first + '.sha256')))

  const block = join(remote, 'fail-put')
  writeFileSync(block, '')
  try {
    await until(() => /Off-server upload failed for ndg-auto-/.test(log()), 'a scheduled upload fails safely', 20000)
    assert.ok(autos().length >= 2 && autos().includes(first), 'failed uploads skip retention')
    await pause(400)
    await setting('enabled', false)
    await command(admin, 'admin apply', /Saved settings are now active/)
  } finally {
    unlinkSync(block)
  }

  const from = admin.messages.length
  await command(admin, 'admin backup create', /Off-server backup verified: ndg-/)
  const archive = admin.messages.slice(from).find(line => /^Off-server backup verified: /.test(line)).split(' ').at(-1)
  const path = join(directory, archive)
  const remoteZip = join(remote, archive)
  const bytes = readFileSync(path)
  const digest = createHash('sha256').update(bytes).digest('hex')
  assert.deepEqual(readFileSync(remoteZip), bytes)
  assert.equal(readFileSync(remoteZip + '.sha256', 'utf8').trim(), bytes.length + ':' + digest)
  await command(admin, 'admin backup upload ' + archive, /Off-server backup verified/)
  await command(admin, 'admin backup verify ' + archive, new RegExp(digest))
  await command(admin, 'admin backup download ' + archive, /local backup already exists/)

  const damaged = Buffer.from(bytes)
  damaged[damaged.length - 1] ^= 1
  writeFileSync(remoteZip, damaged)
  await command(admin, 'admin backup verify ' + archive, /failed SHA-256 verification/)
  unlinkSync(path)
  await command(admin, 'admin backup download ' + archive, /failed SHA-256 verification/)
  assert.equal(existsSync(path), false)
  writeFileSync(remoteZip, bytes)
  await command(admin, 'admin backup download ' + archive, /downloaded and verified/)
  assert.deepEqual(readFileSync(path), bytes)

  const uuid = admin.bot.players[admin.name].uuid
  const recipe = join('plugins/NormalDiscGolf/course-library', uuid, fixture.id + '.yml')
  const saved = readFileSync(recipe, 'utf8')
  unlinkSync(recipe)
  const previewStart = admin.messages.length
  await command(admin, 'admin backup preview ' + archive, /^\/ndg admin backup restore /)
  const confirm = admin.messages.slice(previewStart).find(line => /^\/ndg admin backup restore /.test(line))
  await command(admin, confirm.replace(/^\/ndg /, ''), /Restore scheduled for next restart/)
  writeFileSync(report, JSON.stringify({archive, recipe, saved}))
  console.log('PASS: synthetic compatibility probe, authenticated scheduled and manual uploads, safe upload failure, and verified download.')
}

async function verifyRestored() {
  const { archive, recipe, saved } = JSON.parse(readFileSync(report, 'utf8'))
  assert.equal(readFileSync(recipe, 'utf8'), saved)
  await command(admin, 'admin backup verify ' + archive, /Off-server backup verified/)
  await command(admin, 'admin upgrade check ' + archive, /Matches backup layout/)
  await command(admin, 'party invite ' + guest.name, /Party invite sent/)
  await command(guest, 'party accept', /joined the party/)
  await command(admin, 'library play ' + fixture.id, /Your party queued/)
  await until(() => players.every(state => state.bot.game.gameMode === 'adventure') && admin.turns.length > 0,
    'the restored course opens for both clients', 120000)
  const scored = new Set()
  for (const state of players) await command(state, 'shot timing on', /timing on/)
  while (scored.size < players.length) {
    const active = () => players.find(state => state.name === admin.turns.at(-1))
    await until(() => active() && !scored.has(active()), 'active player is up')
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
  await until(() => players.every(state => state.messages.includes('Round complete!')), 'both clients finish')
  console.log('PASS: downloaded ZIP passed the startup restore, and both Java 1.21.8 clients completed the restored layout.')
}

async function main() {
  await until(() => players.every(state => state.spawned), 'both Java 1.21.8 clients joined', 30000)
  if (process.argv.includes('--after-restart')) await verifyRestored()
  else await saveAndRecover()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const player of players) player.bot.quit()
  await pause(500)
  process.exit(process.exitCode || 0)
})
