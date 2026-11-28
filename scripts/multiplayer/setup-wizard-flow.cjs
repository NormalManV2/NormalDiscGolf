const assert = require('node:assert/strict')
const { once } = require('node:events')
const { readFileSync } = require('node:fs')
const { basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const admin = connect('Config_Admin')
const guest = connect('Config_Guest')
const baseUrl = process.env.NDG_SETUP_HTTPS
const restarted = process.argv.includes('--after-restart')
const itemText = item => JSON.stringify(item?.components ?? [])
const config = 'plugins/NormalDiscGolf/config.yml'

async function open(action) {
  const opened = once(admin.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await action()
  return (await opened)[0]
}

async function wizard(action = () => command(admin, 'admin wizard')) {
  const window = await open(action)
  await until(() => itemText(window.slots[4]).includes('First-run Setup Wizard'), 'setup wizard opens')
  return window
}

async function verify(window, message) {
  const from = admin.messages.length
  await admin.bot.clickWindow(22, 0, 0)
  await until(() => admin.messages.slice(from).some(line => line.includes(message))
    && itemText(window.slots[22]).includes(message), 'verification result appears in chat and in the wizard')
}

async function fresh() {
  await command(guest, 'admin wizard', /do not have permission/)
  const settings = await open(() => command(admin, 'admin'))
  await until(() => itemText(settings.slots[31]).includes('First-run Setup Wizard'), 'admin menu links to setup')
  let window = await wizard(() => admin.bot.clickWindow(31, 0, 0))
  const original = readFileSync(config)
  assert.match(itemText(window.slots[10]), /18 holes/)
  assert.match(itemText(window.slots[12]), /Random/)

  await admin.bot.clickWindow(10, 0, 0)
  await until(() => itemText(window.slots[4]).includes('Choose Default Course Length'), 'length picker')
  assert.match(itemText(window.slots[14]), /6 Holes/)
  await admin.bot.clickWindow(14, 0, 0)
  await until(() => itemText(window.slots[10]).includes('6 holes'), 'draft length selected')
  await admin.bot.clickWindow(12, 0, 0)
  await until(() => itemText(window.slots[4]).includes('Choose Default Terrain'), 'terrain picker')
  assert.match(itemText(window.slots[10]), /Parkland/)
  await admin.bot.clickWindow(10, 0, 0)
  await until(() => itemText(window.slots[12]).includes('Parkland'), 'draft terrain selected')
  assert.deepEqual(readFileSync(config), original, 'choosing draft options does not write config')
  await admin.bot.clickWindow(14, 0, 0)
  await until(() => admin.messages.includes('Course defaults saved and active.'), 'both defaults are saved and applied together')
  assert.deepEqual(readFileSync(config + '.bak'), original, 'one edit backs up the whole original config')
  await command(admin, 'admin get course.holes', /Active 6 .* Saved 6/)
  await command(admin, 'admin get course.terrain', /Active parkland .* Saved parkland/)
  await command(admin, 'admin set queue-countdown-seconds 4', /Saved .* to config/)
  await admin.bot.clickWindow(14, 0, 0)
  await until(() => admin.messages.some(line => line.includes('Review the other pending changes')), 'saving defaults preserves unrelated staged changes')
  await command(admin, 'admin get queue-countdown-seconds', /Active 15 .* Saved 4/)
  await command(admin, 'admin apply', /Saved settings are now active/)

  await admin.bot.clickWindow(20, 0, 0)
  await until(() => admin.messages.includes('[Enter HTTPS Pack URL]'), 'wizard offers a URL entry prompt')
  await command(admin, 'admin wizard url http://localhost/pack.zip', /Enter an HTTPS URL/)
  window = await wizard(() => command(admin, 'admin wizard url ' + baseUrl + '/wrong.zip'))
  await verify(window, 'The HTTPS download differs from the exported ZIP.')
  window = await wizard(() => command(admin, 'admin wizard url ' + baseUrl + '/redirect.zip'))
  await verify(window, 'HTTPS pack matches the exported ZIP byte for byte.')
  window = await wizard(() => command(admin, 'admin wizard url ' + baseUrl + '/downgrade.zip'))
  await verify(window, 'Every pack download redirect must use HTTPS.')
  window = await wizard(() => command(admin, 'admin wizard url ' + baseUrl + '/pack.zip'))
  await verify(window, 'HTTPS pack matches the exported ZIP byte for byte.')
  await command(admin, 'admin get resource-pack.serve', /Active true .* Saved false/)

  // Keep the playground shot deterministic after restarting into the saved HTTPS settings.
  for (const [key, value] of [
    ['course.seed-mode', 'fixed'], ['course.seed', 41], ['flight.wind', false],
    ['preview.enabled', false], ['spectating.replays', false]
  ]) await command(admin, 'admin set ' + key + ' ' + value, /Saved .* to config/)
  console.log('PASS: clean Paper install, operator wizard, scoped default changes, HTTPS-only downloads, redirect handling, and byte-exact verification.')
}

async function afterRestart() {
  let window = await wizard()
  assert.match(itemText(window.slots[10]), /6 holes/)
  assert.match(itemText(window.slots[12]), /Parkland/)
  await command(admin, 'admin get resource-pack.serve', /Active false .* Saved false/)
  await verify(window, 'HTTPS pack matches the exported ZIP byte for byte.')
  const mark = admin.messages.length
  await admin.bot.clickWindow(24, 0, 0)
  await until(() => admin.messages.includes('Your private one-hole setup test is queued.'), 'test round queues')
  await until(() => admin.bot.game.gameMode === 'adventure' && admin.turns.length > 0, 'test reaches the tee', 120000)
  assert.equal(guest.bot.game.gameMode, guest.original.mode, 'the guest stays in the lobby')
  await command(admin, 'status', /^Round: ndg_setup_/)
  assert.ok(admin.messages.includes('Hole: 1/1'))
  assert.ok(admin.messages.some(line => /^Parkland.*Seed 41/.test(line)))
  await command(admin, 'shot timing on', /timing on/)
  await command(admin, 'lie')
  await pause(250)
  const shot = await basketShot(admin.bot)
  assert.ok(shot, 'there is a playable throw from the setup tee')
  await release(admin, shot, 2, null, false)
  await until(() => admin.messages.slice(mark).includes('Round complete!')
    && admin.bot.game.gameMode === admin.original.mode, 'basket completes the private test round')
  assert.deepEqual(inventory(admin.bot), admin.original.items)
  assert.ok(admin.messages.slice(mark).some(line => line.startsWith('Round summary - Setup Test')))
  assert.ok(admin.messages.slice(mark).every(line => !/^(Skill XP|Round reward|Round rating|New course best):/.test(line)))
  await command(admin, 'admin get course.holes', /Active 6 .* Saved 6/)
  console.log('PASS: defaults and HTTPS URL survive restart; the separate one-hole round holes out and restores the player without rewards.')
}

async function main() {
  assert.ok(baseUrl, 'Set NDG_SETUP_HTTPS to the trusted fixture origin')
  await until(() => admin.spawned && guest.spawned, 'both Java 1.21.8 clients join', 30000)
  if (restarted) await afterRestart()
  else await fresh()
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of [admin, guest]) state.bot.quit()
  await pause(550)
  process.exit(process.exitCode || 0)
})
