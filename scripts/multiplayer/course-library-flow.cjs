const assert = require('node:assert/strict')
const { readFileSync, readdirSync, writeFileSync } = require('node:fs')
const { once } = require('node:events')
const { join, resolve } = require('node:path')
const { basket, basketShot, command, connect, inventory, pause, release, until } = require('./parkland-41.cjs')

const report = resolve(process.env.NDG_LIBRARY_TEST_REPORT || 'course-library-restart.json')
const libraryDir = resolve('plugins/NormalDiscGolf/course-library')
const afterRestart = process.argv.includes('--after-restart')
const withFeaturedShelf = process.argv.includes('--featured')
const previous = afterRestart ? JSON.parse(readFileSync(report, 'utf8')) : null
const [leader, partner] = (previous?.names || ['Library_A', 'Library_B'].map(name => name + process.pid % 10000)).map(connect)
const players = [leader, partner]
const curator = withFeaturedShelf ? connect('Feature_Curator') : null
const itemText = item => JSON.stringify(item?.components ?? [])
const courseName = 'Meadow After Hours'
const entry = window => window.slots.slice(9, 45).filter(item => itemText(item).includes('Left click to queue'))

async function open(state, action) {
  const opened = once(state.bot, 'windowOpen', { signal: AbortSignal.timeout(15000) })
  await action()
  const [window] = await opened
  return window
}

async function clubhouseLibrary(state) {
  const clubhouse = await open(state, () => command(state, 'clubhouse'))
  await until(() => itemText(clubhouse.slots[43]).includes('Course Library'), 'Course Library in the clubhouse')
  const library = await open(state, () => state.bot.clickWindow(43, 0, 0))
  await until(() => itemText(library.slots[4]).includes('Course Library'), 'library navigation finishes')
  return library
}

async function prepareParty(host = leader, guest = partner) {
  await command(host, 'party invite ' + guest.name, /Party invite sent/)
  await command(guest, 'party accept', /joined the party/)
}

async function sharedPreview(state, code) {
  const menu = await open(state, () => command(state, 'library code ' + code))
  await until(() => itemText(menu.slots[4]).includes(courseName), 'shared course name appears')
  assert.match(itemText(menu.slots[4]), /Parkland.*1 hole.*Seed 41.*Par 3.*Library Line.*Unrated/)
  assert.match(itemText(menu.slots[33]), /Queue Course/)
  return menu
}

async function featuredShelf(state) {
  const clubhouse = await open(state, () => command(state, 'clubhouse'))
  await until(() => itemText(clubhouse.slots[33]).includes('Featured Courses'), 'Featured Courses tile in the clubhouse')
  const shelf = await open(state, () => state.bot.clickWindow(33, 0, 0))
  await until(() => itemText(shelf.slots[4]).includes('Featured Courses'), 'featured shelf opens')
  return shelf
}

async function featuredPreview(state) {
  const shelf = await featuredShelf(state)
  await until(() => itemText(shelf.slots[9]).includes(courseName), 'the pinned course is listed by name')
  assert.match(itemText(shelf.slots[9]), /Parkland.*1 hole.*Seed 41.*Par 3.*Unrated/)
  assert.equal(shelf.slots[53], null, 'players do not receive the pin control')
  const preview = await open(state, () => state.bot.clickWindow(9, 0, 0))
  await until(() => itemText(preview.slots[27]).includes('Back to Featured Courses'), 'the featured preview is connected to the shelf')
  assert.match(itemText(preview.slots[4]), /Meadow After Hours.*Parkland.*Seed 41/)
  assert.match(itemText(preview.slots[33]), /Queue Course/)
  return preview
}

async function pinFeatured(code) {
  const shelf = await featuredShelf(curator)
  await until(() => itemText(shelf.slots[22]).includes('No featured courses yet')
    && itemText(shelf.slots[53]).includes('Pin by Share Code'), 'only an operator can pin from the empty shelf')
  await curator.bot.clickWindow(53, 0, 0)
  await until(() => curator.messages.includes('[Enter Share Code]'), 'the admin control provides the pin command')
  await command(curator, 'featured pin INVALID!', /share code is invalid or unavailable/)
  await command(leader, 'featured pin ' + code, /do not have permission/)
  const pinned = await open(curator, () => command(curator, 'featured pin ' + code.toLowerCase(), /Course pinned/))
  await until(() => itemText(pinned.slots[9]).includes('Right click to unpin ' + code), 'operator sees the pinned course and unpin control')
  await command(curator, 'featured pin ' + code, /already featured/)
  await command(partner, 'featured unpin ' + code, /do not have permission/)
  const preview = await featuredPreview(partner)
  const back = await open(partner, () => partner.bot.clickWindow(27, 0, 0))
  await until(() => itemText(back.slots[9]).includes(courseName), 'the preview returns to the shelf')
  assert.ok(itemText(preview.slots[22]).includes('Save a Copy'))
  assert.equal(back.slots.slice(9, 45).filter(item => itemText(item).includes('Left click to preview')).length, 1)
}

async function unpinFeatured(code) {
  const preview = await featuredPreview(partner)
  const ownedShelf = await featuredShelf(curator)
  await until(() => itemText(ownedShelf.slots[9]).includes('Right click to unpin ' + code), 'operator can manage the persisted pin')
  const emptyShelf = await open(curator, () => curator.bot.clickWindow(9, 1, 0))
  await until(() => itemText(emptyShelf.slots[22]).includes('No featured courses yet'), 'unpin clears the shelf')
  const mark = partner.messages.length
  const refreshed = await open(partner, () => partner.bot.clickWindow(33, 0, 0))
  await until(() => partner.messages.slice(mark).some(line => line.includes('no longer on the Featured Courses shelf'))
    && itemText(refreshed.slots[22]).includes('No featured courses yet'), 'unpin invalidates an already open preview')
  assert.ok(itemText(preview.slots[27]).includes('Back to Featured Courses'))
  await sharedPreview(partner, code)
  await command(curator, 'featured pin ' + code, /Course pinned/)
  await featuredPreview(partner)
}

async function playRound(queue, isReplay) {
  const marks = players.map(state => state.messages.length)
  const firstTurn = leader.turns.length
  await queue()
  const active = () => players.find(state => state.name === leader.turns.at(-1))
  await until(() => leader.turns.length > firstTurn && players.every(state => state.bot.game.gameMode === 'adventure'),
    'both players reach the tee', 120000)

  let world
  let layout
  for (const state of players) {
    const before = state.messages.length
    await command(state, 'status', /^Round: ndg_ffa_/)
    const messages = state.messages.slice(before)
    const instance = messages.find(line => line.startsWith('Round: ')).slice(7)
    world ||= instance
    assert.equal(world, instance, 'party members share the same fresh world')
    assert.ok(messages.includes('Hole: 1/1'))
    assert.ok(messages.some(line => /^Parkland.*Seed 41.*Preset Library Line/.test(line)
      && isReplay === line.includes('Course Library')))
    layout = messages.find(line => line.startsWith('Layout: '))
    await command(state, 'shot timing on', /timing on/)
  }

  const scored = new Set()
  let geometry
  for (let turn = 0; turn < players.length; turn++) {
    await until(() => active() && !scored.has(active()), 'next player on the tee')
    const state = active()
    const before = state.messages.length
    await command(state, 'lie')
    await pause(250)
    if (!geometry) {
      const tee = state.bot.entity.position.clone()
      let cap
      await until(() => (cap = state.bot.findBlock({matching: state.bot.registry.blocksByName.yellow_concrete.id,
        maxDistance: 55})), 'the goal blocks reach the client')
      const path = [tee.offset(0, -1, 0), tee.offset(8, -1, 4), tee.offset(16, -1, 8), cap.position]
      geometry = { tee: tee.toArray(), cap: cap.position.toArray(), palette: path.map(point => state.bot.blockAt(point)?.name), layout }
    }
    const shot = await basketShot(state.bot, 'driver', basket)
    assert.ok(shot, 'a clear line reaches the actual basket')
    await release(state, shot)
    await until(() => state.messages.slice(before).some(line => line.startsWith(state.name + ' completed hole 1!')),
      state.name + ' holes out')
    scored.add(state)
  }
  await until(() => players.every((state, index) => state.messages.slice(marks[index]).includes('Round complete!')
    && state.bot.game.gameMode === state.original.mode
    && state.bot.entity.position.distanceTo(state.original.position) < 2), 'the round ends and both players return to the lobby')
  for (const [index, state] of players.entries()) {
    assert.deepEqual(inventory(state.bot), state.original.items)
    const messages = state.messages.slice(marks[index])
    assert.ok(messages.some(line => /^Score: -2 \(1 stroke, par 3\)/.test(line)))
    assert.equal(messages.some(line => line.startsWith('Round rating: ')), !isReplay, 'replays are unrated')
    assert.ok(messages.some(line => line.startsWith('Round reward: +')), 'the basket still awards credits')
  }
  return { world, geometry }
}

async function main() {
  await until(() => players.every(state => state.spawned) && (!curator || curator.spawned),
    'all Java 1.21.8 clients join', 30000)
  if (!afterRestart) {
    const empty = await clubhouseLibrary(leader)
    await until(() => itemText(empty.slots[22]).includes('No saved courses'), 'initial library is empty')
    leader.bot.closeWindow(empty)
    await prepareParty()
    const original = await playRound(() => command(leader, 'queue ffa new parkland library-line', /queued for FFA/), false)
    const summary = await open(leader, () => command(leader, 'summary'))
    await until(() => itemText(summary.slots[33]).includes('Save Course'), 'generated round summary offers a save')
    await leader.bot.clickWindow(33, 0, 0)
    await until(() => itemText(summary.slots[33]).includes('Course Saved'), 'save is confirmed in the summary')
    const savedFromSummary = await open(leader, () => leader.bot.clickWindow(33, 0, 0))
    await until(() => entry(savedFromSummary).length === 1, 'one saved entry appears immediately')
    const playersWithSaves = readdirSync(libraryDir).filter(name => name !== 'shares')
    assert.equal(playersWithSaves.length, 1)
    const files = readdirSync(join(libraryDir, playersWithSaves[0]))
    assert.equal(files.length, 1)
    const id = files[0].slice(0, -4)
    await command(partner, 'library rename ' + id + ' Other Name', /unavailable in your library/)
    await command(partner, 'library share ' + id, /unavailable in your library/)
    const manage = await open(leader, () => leader.bot.clickWindow(9, 1, 0))
    await until(() => itemText(manage.slots[20]).includes('Rename Course'), 'right-click opens management')
    await leader.bot.clickWindow(20, 0, 0)
    await until(() => leader.messages.includes('[Enter Course Name]'), 'rename opens the chat prompt')
    await command(leader, 'library rename ' + id + ' ' + 'A'.repeat(33), /1 to 32 visible characters/)
    const named = await open(leader, () => command(leader, 'library rename ' + id + ' ' + courseName, /Course renamed to/))
    await until(() => itemText(named.slots[22]).includes('Create Share Code'), 'sharing requires an explicit action')
    await leader.bot.clickWindow(22, 0, 0)
    const shareMessage = new RegExp('^Share code for ' + courseName + ': ([A-HJ-NP-Z2-9]{7})$')
    await until(() => leader.messages.some(line => shareMessage.test(line)), 'owner receives a short code')
    const code = leader.messages.find(line => shareMessage.test(line)).match(shareMessage)[1]
    await until(() => leader.messages.includes('[Copy ' + code + ']'), 'the copy action is sent to the owner')

    const friendLibrary = await clubhouseLibrary(partner)
    await until(() => itemText(friendLibrary.slots[53]).includes('Enter Share Code'), 'the clubhouse library has a code entry')
    await partner.bot.clickWindow(53, 0, 0)
    await until(() => partner.messages.includes('[Enter Share Code]'), 'the clubhouse offers a command prompt')
    await command(partner, 'library code NOT-A-CODE', /share code is invalid or unavailable/)
    const preview = await sharedPreview(partner, code.toLowerCase())
    await until(() => itemText(preview.slots[22]).includes('Save a Copy'), 'the preview has a save action')
    await command(partner, 'library play-code ' + code, /Only the party leader/)
    assert.deepEqual(readdirSync(libraryDir).filter(name => name !== 'shares'), playersWithSaves, 'previewing does not add a copy')
    if (withFeaturedShelf) {
      await pinFeatured(code)
      console.log('PASS: curator-only pins, permission checks, duplicate prevention, and clubhouse shelf discovery')
    }
    writeFileSync(report, JSON.stringify({ names: players.map(state => state.name), original, id, code }, null, 2))
    console.log('PASS: two-player FFA, summary save, naming, code creation, and a friend previewing from the clubhouse')
    return
  }

  const otherLibrary = await clubhouseLibrary(partner)
  await until(() => itemText(otherLibrary.slots[22]).includes('No saved courses'), 'the other player has a separate library')
  partner.bot.closeWindow(otherLibrary)
  await command(partner, 'library play ' + previous.id, /unavailable in your library/)
  const ownLibrary = await clubhouseLibrary(leader)
  await until(() => entry(ownLibrary).length === 1, 'the saved entry persists after restart')
  assert.match(itemText(entry(ownLibrary)[0]), /Meadow After Hours.*Parkland.*1 hole.*Seed 41.*Par 3.*Library Line/)
  const manage = await open(leader, () => leader.bot.clickWindow(9, 1, 0))
  await until(() => itemText(manage.slots[22]).includes(previous.code), 'the owner still sees the same share code')
  leader.bot.closeWindow(manage)

  await prepareParty(partner, leader)
  if (withFeaturedShelf) {
    await featuredPreview(partner)
    await command(leader, 'featured play ' + previous.code, /Only the party leader/)
  } else {
    await sharedPreview(partner, previous.code)
  }
  const replay = await playRound(async () => {
    await partner.bot.clickWindow(33, 0, 0)
    await until(() => players.every(state => state.messages.some(line => line.startsWith('Your party queued ' + courseName))),
      'both players receive queue confirmation')
  }, true)
  assert.notEqual(replay.world, previous.original.world)
  assert.deepEqual(replay.geometry, previous.original.geometry, 'tee, basket, course name and block samples match after config changes')
  const fromCode = withFeaturedShelf ? await featuredPreview(partner) : await sharedPreview(partner, previous.code)
  await until(() => itemText(fromCode.slots[22]).includes('Save a Copy'), 'queueing from a code did not save the layout')
  const saved = await open(partner, () => partner.bot.clickWindow(22, 0, 0))
  await until(() => itemText(saved.slots[22]).includes('Saved to Your Library'), 'friend saves a copy from the preview')
  partner.bot.closeWindow(saved)
  await command(partner, (withFeaturedShelf ? 'featured save ' : 'library save-code ') + previous.code,
    /already in your Course Library/)
  if (withFeaturedShelf) await unpinFeatured(previous.code)

  const owner = await open(leader, () => command(leader, 'library manage ' + previous.id))
  await until(() => itemText(owner.slots[24]).includes('Revoke Share Code'), 'the issuer can revoke their code')
  await leader.bot.clickWindow(24, 0, 0)
  await until(() => leader.messages.includes('Course share code revoked.'), 'revocation is confirmed')
  const stale = partner.bot.currentWindow
  const previousMessages = partner.messages.length
  if (withFeaturedShelf) {
    const empty = await open(partner, () => partner.bot.clickWindow(33, 0, 0))
    await until(() => itemText(empty.slots[22]).includes('No featured courses yet'), 'revocation clears the refreshed shelf')
    partner.bot.closeWindow(empty)
  } else {
    await partner.bot.clickWindow(33, 0, 0)
  }
  const revokedMessage = withFeaturedShelf ? 'no longer on the Featured Courses shelf' : 'share code is invalid or unavailable'
  await until(() => partner.messages.slice(previousMessages).some(line => line.includes(revokedMessage)),
    'a preview opened before revocation cannot queue the course')
  if (!withFeaturedShelf) partner.bot.closeWindow(stale)

  const copy = await clubhouseLibrary(partner)
  await until(() => entry(copy).length === 1, 'the copy survives revocation without duplicates')
  assert.match(itemText(entry(copy)[0]), /Meadow After Hours.*Parkland.*Seed 41/)
  console.log('PASS: same code after restart, friend-led party completion on identical geometry, deduplicated copy, and live revocation')
  if (withFeaturedShelf) {
    const empty = await featuredShelf(partner)
    await until(() => itemText(empty.slots[22]).includes('No featured courses yet'), 'revocation automatically removes the featured listing')
    console.log('PASS: pin persistence, featured preview/save/party replay, unpin, and automatic removal of revoked shares')
  }
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(async () => {
  for (const state of players) state.bot.quit()
  curator?.bot.quit()
  await pause(650)
  process.exit(process.exitCode || 0)
})
