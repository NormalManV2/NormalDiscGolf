const assert = require('node:assert/strict')
const { once } = require('node:events')
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

// Opening hole of the seed-41 Parkland fixture. Disable wind for scripted shots.
const tee = new Vec3(56.5, 66, 56.5)
const basket = new Vec3(88.5, 66.9, 72.5)
const port = Number(process.env.NDG_TEST_PORT || 25569)
const version = process.env.NDG_TEST_VERSION || '1.21.8'
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))

async function until(predicate, label, timeout = 15000) {
  const deadline = Date.now() + timeout
  while (!predicate()) {
    if (Date.now() >= deadline) throw Error('Timed out: ' + label)
    await pause(50)
  }
}

function attachClientMovementTicks(bot) {
  if (process.env.NDG_TEST_MOVEMENT_TICK_END === '1') {
    const write = bot._client.write.bind(bot._client)
    bot._client.write = (packet, data) => {
      const result = write(packet, data)
      // Paper 26.3 expects at most one position update before the client ends its tick.
      if (packet === 'position' || packet === 'position_look') write('tick_end', {})
      return result
    }
  }
}

function connect(name) {
  const bot = mineflayer.createBot({host:'127.0.0.1', port, username:name, version, auth:'offline', physicsEnabled:false})
  attachClientMovementTicks(bot)
  const state = {bot, name, messages:[], turns:[], spawned:false}
  bot.on('error', error => { state.error = error.message })
  bot.on('kicked', reason => { state.error = JSON.stringify(reason) })
  bot.once('spawn', () => {
    state.original = {position:bot.entity.position.clone(), mode:bot.game.gameMode, items:inventory(bot)}
    state.spawned = true
  })
  bot.on('messagestr', (line, kind) => {
    if (kind === 'game_info') return
    state.messages.push(line)
    const turn = line.match(/^([\w]+)'s turn(?: •.*)?$/)
    if (turn) state.turns.push(turn[1])
    if (/^Hole \d+: |^Match result: /.test(line) && name.includes('A')) console.log(line)
  })
  return state
}

function inventory(bot) {
  return bot.inventory.items().map(item => [item.name,item.count]).sort()
}

async function command(state, text, response) {
  const before = state.messages.length
  state.bot.chat('/ndg ' + text)
  if (response) await until(() => state.messages.slice(before).some(line => response.test(line)), text)
}

async function openCourseLobbies(state, modeSlot) {
  const opened = once(state.bot, 'windowOpen', {signal: AbortSignal.timeout(10000)})
  await Promise.all([opened, state.bot.clickWindow(modeSlot, 0, 0)])
  await until(() => state.bot.currentWindow?.slots[48]?.name === 'emerald', 'Quick Join in course lobbies')
  return state.bot.currentWindow
}

async function clickModeJoin(state, modeSlot) {
  await openCourseLobbies(state, modeSlot)
  await state.bot.clickWindow(48, 0, 0)
}

const eyeOf = bot => bot.entity.position.offset(0, bot.entity.eyeHeight, 0)
const direction = (yaw,pitch) => new Vec3(Math.cos(yaw)*Math.cos(pitch), Math.sin(pitch), Math.sin(yaw)*Math.cos(pitch))
const smoothRamp = (value, start, end) => {
  const progress = Math.max(0, Math.min(1, (value-start)/(end-start)))
  return progress * progress * (3-2*progress)
}

async function basketShot(bot, disc = 'driver', target = basket) {
  const putting = disc === 'putting'
  const [speed, ticks, turn, fade, gravity] = {
    driver: [2.675, 100, -2, 2, 5/220],
    midrange: [1.9 * 1.07, 80, -1, 1, 5/220],
    putter: [1.3 * 1.07, 60, 0, 1, 4/220],
    putting: [1.3 * 1.07 * .68, 30, 0, 0, .014]
  }[disc]
  const eye = eyeOf(bot)
  const heading = Math.atan2(target.z-eye.z, target.x-eye.x)
  const blocked = new Map()
  function blockedAt(point) {
    const key = point.floored().toString()
    if (!blocked.has(key)) {
      const block = bot.blockAt(point,false)
      blocked.set(key, block?.boundingBox==='block' || block?.name==='water')
    }
    return blocked.get(key)
  }
  function reaches(power, yaw, pitch) {
    const headingVector = direction(yaw,pitch)
    const position = eye.plus(headingVector.scaled(.35))
    const velocity = headingVector.scaled(speed*power)
    for (let tick=1; tick<=Math.min(75,ticks+1); tick++) {
      for (let sample=1; sample<=8; sample++) {
        const point = position.plus(velocity.scaled(sample/8))
        if (Math.abs(point.x-target.x)<1.23 && Math.abs(point.z-target.z)<1.23 &&
            Math.abs(point.y-target.y)<.90) return true
        if (blockedAt(point)) return false
      }
      position.add(velocity)
      velocity.scale(putting ? .96 : .98)
      if (putting || tick > ticks*.2) velocity.y -= gravity
      if (putting) continue
      const ratio = Math.hypot(velocity.x, velocity.z) / (speed / 1.07)
      const early = turn * smoothRamp(ratio,.55,.96) * (1-smoothRamp(tick,10,31)) * .0115
      const late = Math.min(.032, fade * (1-smoothRamp(ratio,.42,.83)) * smoothRamp(tick,5,27) * ratio*ratio * .060)
      const angle = early + late
      const x = velocity.x
      velocity.x = x*Math.cos(angle) + velocity.z*Math.sin(angle)
      velocity.z = velocity.z*Math.cos(angle) - x*Math.sin(angle)
    }
    return false
  }

  for (const power of [60,65,70,55,75,80,85,90,95,100,50,45,40,35,30,25,20]) {
    for (let yaw=-520; yaw<=520; yaw+=10) {
      // Keep protocol packets moving even when a scoring line takes a long search.
      if ((yaw+520)%100===0) await new Promise(resolve=>setImmediate(resolve))
      for (let pitch=-360; pitch<=590; pitch+=10) {
        const headingAtRelease = heading+yaw/1000
        if ([0,-.004,.004].every(error => reaches(power/100, headingAtRelease+error, pitch/1000))) {
          return {power, aim:eye.plus(direction(headingAtRelease,pitch/1000).scaled(10))}
        }
      }
    }
  }
  return null
}

function shortShot(bot, power = 20, pitch = -.45, target = basket) {
  const eye = eyeOf(bot)
  const heading = Math.atan2(target.z-eye.z, target.x-eye.x)
  return {power, aim:eye.plus(direction(heading,pitch).scaled(10))}
}

async function release(state, shot, slot = 2, throwCommand = null, expectXp = true) {
  const {bot} = state
  bot.setQuickBarSlot(slot)
  await bot.lookAt(shot.aim,true)
  const before = state.messages.length
  let locked = false, clicked = false
  const acceptMeter = bar => {
    const title = String(bar.title)
    if (!locked && /^(Throw|Putt) Power:/.test(title) && Math.round(bar.health*100)===shot.power) {
      locked = true
      bot.swingArm('right')
    } else if (locked && !clicked && title.startsWith('Release Timing:') && Math.abs(bar.health-.5)<.02) {
      clicked = true
      bot.swingArm('right')
    }
  }
  bot.on('bossBarUpdated', acceptMeter)
  bot.on('bossBarCreated', acceptMeter)
  try {
    if (throwCommand === null) bot.activateItem()
    else bot.chat(throwCommand)
    await until(() => state.messages.slice(before).includes('Disc released.'), state.name + ' release')
    assert.ok(state.messages.slice(before).includes('Clean release'), 'meter releases at the center')
    if (expectXp) await until(() => state.messages.slice(before).some(line => line.startsWith('Skill XP:')), state.name + ' shot resolves')
  } finally {
    bot.off('bossBarUpdated', acceptMeter)
    bot.off('bossBarCreated', acceptMeter)
  }
}

module.exports = {tee,basket,until,pause,connect,attachClientMovementTicks,inventory,command,openCourseLobbies,clickModeJoin,basketShot,shortShot,release}
