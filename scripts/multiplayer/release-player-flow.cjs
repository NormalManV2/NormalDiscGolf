const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { once } = require('node:events')
const fs = require('node:fs')
const path = require('node:path')
const { inspect } = require('node:util')
const { connect, until, command: issueCommand, pause } = require('./parkland-41.cjs')

const unavailable = process.argv.includes('--unavailable-pack')
const checkout = process.argv.includes('--checkout')
const gate = process.argv.includes('--required-pack')
const expectedUrl = process.env.NDG_TEST_PACK_URL
const players = checkout || gate ? [] : ['ReleaseA', 'ReleaseB'].map(prefix=>connect(prefix + process.pid%100000))
const [lead, guest] = players
const offers = []

async function command(...args) {
  await issueCommand(...args)
  await pause(650)
}

async function menu(state, text, slot, material) {
  const opened = once(state.bot, 'windowOpen')
  await command(state, text)
  const [window] = await opened
  await until(()=>window.slots[slot]?.name===material, text + ' loads the expected item')
  return window
}

async function download(pack, required = false) {
  assert.equal(pack.url, expectedUrl)
  assert.equal(pack.forced, required)
  const head = await fetch(pack.url, { method: 'HEAD' })
  const zip = await fetch(pack.url)
  assert.equal(head.status, 200)
  assert.equal(zip.status, 200)
  const bytes = Buffer.from(await zip.arrayBuffer())
  assert.equal(head.headers.get('content-length'), String(bytes.length))
  assert.equal(zip.headers.get('content-type'), 'application/zip')
  assert.ok(bytes.includes(Buffer.from('pack.mcmeta')))
  assert.equal(createHash('sha1').update(bytes).digest('hex'), pack.hash)
  assert.equal((await fetch(pack.url + '/extra')).status, 404)
  return bytes.length
}

async function requiredPackFlow() {
  const state = connect('Required' + process.pid%100000)
  players.push(state)
  state.bot._client.on('add_resource_pack', pack=>offers.push(pack))
  await until(()=>state.spawned && offers.length===1, 'required pack arrives at login')
  assert.equal(offers[0].forced, true)
  const notReady = /^Accept the server resource pack and wait for it to finish loading before queueing\.$/
  await command(state, 'queue match', notReady)
  const failed = state.messages.length
  state.bot._client.write('resource_pack_receive', { uuid: offers[0].uuid, result: 1 })
  await until(()=>state.messages.slice(failed).some(line=>line.startsWith('The disc golf pack is required.')),
    'failed download displays the required pack notice')
  await command(state, 'queue match', notReady)
  await command(state, 'pack retry', /^Disc models offered again\.$/)
  await until(()=>offers.length===2, 'retry sends another required offer')
  await download(offers[1], true)
  state.bot._client.write('resource_pack_receive', { uuid: offers[1].uuid, result: 3 })
  state.bot._client.write('resource_pack_receive', { uuid: offers[1].uuid, result: 0 })
  await until(()=>state.messages.includes('Parkland disc models are ready.'), 'the server sees the successful load')
  await command(state, 'queue match', /^You have been queued for Match Play\.$/)
  console.log('PASS: the required pack gates queues while pending and after a failed download, then admits the player after retry')
}

async function checkoutFlow() {
  const directory = process.env.NDG_TEST_PROFILE_DIR
  assert.ok(directory, 'set NDG_TEST_PROFILE_DIR to the loopback server’s player-profiles directory')
  const name = 'Checkout' + process.pid%100000
  const uuid = createHash('md5').update('OfflinePlayer:' + name).digest()
  uuid[6] = (uuid[6] & 15) | 0x30
  uuid[8] = (uuid[8] & 63) | 0x80
  const id = uuid.toString('hex').replace(/^(........)(....)(....)(....)(............)$/, '$1-$2-$3-$4-$5')
  fs.writeFileSync(path.join(directory, id + '.yml'), 'disc-shop:\n  credits: 1000\n', { flag:'wx' })
  const shopper = connect(name)
  players.push(shopper)
  await until(()=>shopper.spawned, 'funded checkout fixture connects')
  const shop = await menu(shopper, 'shop', 11, 'slime_ball')
  await shopper.bot.clickWindow(11,0,0)
  await until(()=>shop.slots[31]?.name==='emerald', 'inspection presents a confirmation in the same shop')
  assert.equal(shopper.bot.currentWindow.id, shop.id)
  await command(shopper, 'profile', /^Disc shop balance: 1000 credits$/)
  const bought = shopper.messages.length
  await shopper.bot.clickWindow(31,0,0)
  await until(()=>shopper.messages.slice(bought).includes('Unlocked Float for 60 credits.'), 'confirm buys Float')
  await shopper.bot.clickWindow(31,0,0)
  await until(()=>shopper.messages.slice(bought).includes('Packed Float for your next round.'), 'second click packs the owned disc')
  shopper.bot.closeWindow(shop)

  await command(shopper, 'shop buy float', /^You already own Float\.$/)
  await command(shopper, 'shop buy anchor', /^Unlocked Anchor for 70 credits\.$/)
  await command(shopper, 'shop pack anchor', /^Packed Anchor for your next round\.$/)
  await command(shopper, 'shop buy glider', /^Unlocked Glider for 90 credits\.$/)
  await command(shopper, 'shop pack glider', /^Your bag is full\./)
  const bag = await menu(shopper, 'bag', 51, 'bundle')
  await shopper.bot.clickWindow(51,0,0)
  await until(()=>bag.slots[31]?.name==='emerald', 'the upgrade asks for confirmation')
  const upgraded = shopper.messages.length
  await shopper.bot.clickWindow(31,0,0)
  await until(()=>shopper.messages.slice(upgraded).includes('Bag upgraded to 7 slots for 180 credits.'), 'bag upgrade charges once')
  shopper.bot.closeWindow(bag)
  await command(shopper, 'shop pack glider', /^Packed Glider for your next round\.$/)
  await command(shopper, 'decals buy gold-ring', /^Unlocked Gold Ring for 60 credits\./)
  await command(shopper, 'decals buy gold-ring', /^You already own Gold Ring\.$/)
  await command(shopper, 'decals equip gold-ring', /^Equipped Gold Ring as your score decal\.$/)

  const left = once(shopper.bot, 'end')
  shopper.bot.quit()
  await left
  const reconnect = connect(name)
  players.push(reconnect)
  await until(()=>reconnect.spawned, 'the same profile reconnects')
  await command(reconnect, 'profile', /^Disc shop balance: 540 credits$/)
  await command(reconnect, 'decals equip gold-ring', /^Gold Ring is already selected\.$/)
  const reopened = await menu(reconnect, 'bag', 51, 'bundle')
  assert.match(inspect(reopened.slots[51], { depth:12 }), /6\/7/)
  console.log('PASS: shop confirmation, exact wallet debits, duplicate purchase rejection, bag upgrade, and restored decal and packed discs')
}

async function main() {
  if (checkout) return checkoutFlow()
  if (gate) return requiredPackFlow()
  lead.bot._client.on('add_resource_pack', pack=>offers.push(pack))
  await until(()=>players.every(state=>state.spawned), 'two ordinary 1.21.8 players join')
  if (!unavailable) {
    assert.ok(expectedUrl, 'set NDG_TEST_PACK_URL to the configured non-loopback pack URL')
    assert.notEqual(new URL(expectedUrl).hostname, '127.0.0.1')
    await until(()=>offers.length===1, 'configured pack arrives at login')
    const mark = lead.messages.length
    lead.bot._client.write('resource_pack_receive', { uuid: offers[0].uuid, result: 2 })
    await until(()=>lead.messages.slice(mark).some(line=>line.startsWith('Using vanilla disc icons.')), 'decline keeps the player in game')
  }
  const shop = await menu(lead, 'shop', 10, 'slime_ball')
  assert.doesNotMatch(inspect(shop.slots[10], { depth: 12 }), /ndg:starter-putter/)
  await command(lead, 'shop equip float', /^Unlock Float first\.$/)
  await command(lead, 'shop buy float', /^You need 60 more credits\.$/)
  await command(lead, 'decals equip gold-ring', /^Unlock Gold Ring before equipping it\.$/)
  if (!unavailable) {
    await command(lead, 'pack retry', /^Disc models offered again\.$/)
    await until(()=>offers.length===2, 'retry offers the pack a second time')
    const bytes = await download(offers[1])
    lead.bot._client.write('resource_pack_receive', { uuid: offers[1].uuid, result: 3 })
    lead.bot._client.write('resource_pack_receive', { uuid: offers[1].uuid, result: 0 })
    await until(()=>lead.messages.includes('Parkland disc models are ready.'), 'successful pack status reaches the plugin')
    await until(()=>/ndg:starter-putter/.test(inspect(shop.slots[10], { depth: 12 })), 'the same open shop refreshes to the custom model')
    console.log('PASS: optional pack decline and retry, open menu model refresh, and ' + bytes + ' ZIP bytes with matching SHA-1 at ' + expectedUrl)
  } else {
    await command(lead, 'pack retry', /^Pack delivery is unavailable for this connection\.$/)
    console.log('PASS: an unavailable optional pack leaves the shop and vanilla icons usable')
  }
  lead.bot.closeWindow(shop)
  const bag = await menu(lead, 'bag', 0, 'slime_ball')
  await lead.bot.clickWindow(2,0,0)
  await lead.bot.clickWindow(1,0,0)
  const last = lead.messages.length
  await lead.bot.clickWindow(0,0,0)
  await until(()=>lead.messages.slice(last).includes('Keep at least one disc in your bag.'), 'the last packed disc cannot be removed')
  lead.bot.closeWindow(bag)
  await command(lead, 'shop pack starter-midrange', /^Packed .* for your next round\.$/)
  await command(lead, 'shop pack starter-driver', /^Packed .* for your next round\.$/)
  await command(lead, 'queue invalid', /^Unknown round format\./)
  await command(lead, 'queue weekly', /^Use \/ndg tournament signup/)
  await command(lead, 'party invite ' + guest.name, /^Party invite sent/)
  await command(guest, 'party accept', /joined the party/)
  await command(guest, 'queue ffa', /^Only the party leader/)
  await command(lead, 'queue ffa', /^Your party has been queued/)
  const secondQueue = lead.messages.length
  await command(lead, 'queue ace-race')
  await until(()=>lead.messages.slice(secondQueue).some(line=>/already (queued|in an active or preparing round)/.test(line)),
    'an entrant cannot join a second mode')
  await until(()=>lead.turns.length>0 && players.every(state=>state.bot.game.gameMode==='adventure'),
    'both ordinary players receive a playable kit', 120000)
  await command(lead, 'shop buy float', /^You can change your collection and bag between rounds\.$/)
  await command(guest, 'decals equip none', /^Use the score decal shop between rounds\.$/)
  assert.deepEqual(lead.bot.inventory.slots.slice(36,39).map(item=>item?.name), ['slime_ball', 'heart_of_the_sea', 'fire_charge'])
  for (let step=0;step<players.length;step++) {
    const mark = lead.turns.length
    const active = players.find(state=>state.name===lead.turns.at(-1))
    const waiting = players.find(state=>state!==active)
    const rejected = waiting.messages.length
    waiting.bot.chat('/throwdisc driver')
    await until(()=>waiting.messages.slice(rejected).some(line=>/not your .*turn|Wait for your turn/i.test(line)), 'out-of-turn throw rejected')
    await command(active, 'concede', /^Hole conceded:/)
    await until(()=>lead.turns.length>mark || lead.messages.includes('Round complete!'), 'the concession advances the card')
  }
  await until(()=>players.every(state=>state.bot.game.gameMode===state.original.mode), 'both players return to the lobby')
  for (const state of players) {
    assert.ok(!state.messages.some(line=>/^Round reward:|^Round rating:|^Season \d+ rating:/.test(line)))
    await command(state, 'profile', /^Disc shop balance: 0 credits$/)
  }
  console.log('PASS: funds, ownership, bag minimum, party leadership, duplicate queues, active shop restrictions, out-of-turn throws, and clean concession')
}

const deadline = setTimeout(()=>{ console.error('Release player check exceeded its deadline'); process.exit(1) }, 220000)
main().catch(error=>{ console.error(error); process.exitCode=1 }).finally(async()=>{
  clearTimeout(deadline)
  for (const {bot} of players) bot.quit()
  await pause(400)
  process.exit(process.exitCode || 0)
})
