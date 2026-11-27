const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { inspect } = require('node:util')
const { simplify } = require('prismarine-nbt')
const { command, connect, inventory, until } = require('./parkland-41.cjs')

const players = ['MenuTextureA', 'MenuTextureB'].map(connect)
const [textured, vanilla] = players
const styled = /[\ue730-\ue739]/
for (const state of players) {
  state.offers = []
  state.windows = []
  state.bot._client.on('add_resource_pack', pack => state.offers.push(pack))
  state.bot._client.on('open_window', packet => state.windows.push({
    id: packet.windowId, title: JSON.stringify(simplify(packet.windowTitle))
  }))
}

const slot = (state, index) => state.bot.currentWindow?.slots[index]
const model = item => inspect(item?.components, { depth: 12 })
const hasModel = (state, index, id) => model(slot(state, index)).includes('ndg:' + id)
const status = (state, result) => {
  const pack = state.offers.at(-1)
  state.bot._client.write('resource_pack_receive', { uuid: pack.uuid, result })
}

async function open(state, action, title, glyph) {
  const previous = state.windows.length
  await action()
  await until(() => state.windows.length > previous && state.windows.at(-1).title.includes(title),
    title + ' opens')
  assert.equal(styled.test(state.windows.at(-1).title), glyph, title + ' uses the correct frame')
}

async function main() {
  await until(() => players.every(state => state.spawned && state.offers.length === 1), 'both clients receive the optional pack', 30000)
  const pack = textured.offers[0]
  assert.equal(pack.forced, false)
  const response = await fetch(pack.url)
  assert.ok(response.ok)
  const bytes = Buffer.from(await response.arrayBuffer())
  assert.equal(createHash('sha1').update(bytes).digest('hex'), pack.hash)
  for (const path of ['assets/minecraft/font/default.json', 'assets/ndg/textures/gui/panel/rounds.png',
    'assets/ndg/textures/gui/panel/shop.png', 'assets/ndg/textures/item/gui/clock.png',
    'assets/ndg/items/gui/clock.json', 'assets/ndg/items/starter-driver.json']) assert.ok(bytes.includes(path), path)

  const original = inventory(textured.bot)
  await open(textured, () => command(textured, 'shop'), 'Parkland Pro Shop', false)
  await until(() => slot(textured, 10)?.name === 'slime_ball', 'vanilla shop inventory loads')
  const sameShop = textured.windows.at(-1).id
  assert.equal(hasModel(textured, 4, 'gui/clock'), false)
  assert.equal(hasModel(textured, 10, 'starter-putter'), false)
  status(textured, 3)
  status(textured, 4)
  status(textured, 0)
  await until(() => styled.test(textured.windows.at(-1).title) && hasModel(textured, 4, 'gui/clock')
    && hasModel(textured, 10, 'starter-putter'), 'the open shop redraws its title, control, and disc together')
  assert.equal(textured.windows.at(-1).id, sameShop)

  await textured.bot.clickWindow(10,0,0)
  await until(() => hasModel(textured, 33, 'gui/basket'), 'the disc detail page has a textured practice button')
  await open(textured, () => textured.bot.clickWindow(48,0,0), 'Your Disc Bag', true)
  await until(() => hasModel(textured, 51, 'gui/bag'), 'bag upgrade shows the bag icon')
  await open(textured, () => textured.bot.clickWindow(53,0,0), 'Score Decals', true)
  await until(() => hasModel(textured, 18, 'gui/nav'), 'the decal menu uses navigation artwork')
  await open(textured, () => command(textured, 'clubhouse'), 'Parkland Clubhouse', true)
  await until(() => hasModel(textured, 37, 'gui/clubhouse'), 'clubhouse visit uses the new icon')
  await open(textured, () => textured.bot.clickWindow(51,0,0), 'Disc Golf Guide', true)
  await until(() => hasModel(textured, 20, 'gui/basket'), 'the guide has the practice tile')
  await open(textured, () => textured.bot.clickWindow(20,0,0), 'Putting Green', true)
  await until(() => hasModel(textured, 10, 'starter-putter'), 'practice keeps the disc model')
  await open(textured, () => command(textured, 'menu'), 'Round Picker', true)
  await until(() => hasModel(textured, 11, 'gui/rounds'), 'FFA uses the new flag icon')
  await open(textured, () => textured.bot.clickWindow(11,0,0), 'Course Lobbies', true)
  await until(() => hasModel(textured, 48, 'gui/rounds'), 'Quick Join keeps its textured action')

  status(vanilla, 1)
  await command(vanilla, 'pack status', /Enable server resource packs/)
  await open(vanilla, () => command(vanilla, 'menu'), 'Round Picker', false)
  await until(() => slot(vanilla, 11)?.name === 'emerald', 'a player who declined sees the vanilla FFA icon')
  assert.equal(hasModel(vanilla, 11, 'gui/rounds'), false)

  const sameLobby = textured.windows.at(-1).id
  status(textured, 7)
  await until(() => !styled.test(textured.windows.at(-1).title) && slot(textured, 48)?.name === 'emerald'
    && !hasModel(textured, 48, 'gui/rounds'), 'discarding the pack restores the open lobby title and ready action')
  assert.equal(textured.windows.at(-1).id, sameLobby)
  assert.deepEqual(inventory(textured.bot), original)
  console.log('PASS: eight themed menus, preserved disc models, functional navigation, and live vanilla fallback for a second client.')
}

main().catch(error => { console.error(error.stack); process.exitCode = 1 }).finally(() => {
  for (const {bot} of players) bot.quit()
})
