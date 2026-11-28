# NormalDiscGolf

A multiplayer Minecraft disc-golf plugin with recreational FFA, alternate-shot, Best Shot and Worst Shot doubles, head-to-head Match Play, and generated parkland courses.

## Build and install

Use Java 21 and run `./gradlew build` to build `build/libs/NormalDiscGolf-1.0-SNAPSHOT-all.jar`. Drop the built jar into the `plugins` folder of a Paper or Spigot-compatible Minecraft 1.21.8 server and start it. NDG creates its configuration, resource pack, and clubhouse automatically. WorldEdit is optional for legacy schematic integration.

The default round has 18 holes. Set `course.holes: 3` in `plugins/NormalDiscGolf/config.yml` for a shorter round. The supported range is 1–18. The seed controls repeatable ground layout. Settings apply to subsequently created rounds; restart after changing configuration.

The 18-hole course alternates six layouts with tee distances from roughly 36 to 115 blocks and pars 3 through 5. The seed controls the order within each group, the orientation of the holes, and obstacle placement. Look for spruce stands, birch groves, rock clusters, sand bunkers, and pond bends. Tees face the opening fairway on doglegs.

## Play

- Use `/ndg party invite <player>` and have friends run `/ndg party accept`. The leader runs `/ndg queue ffa`, `/ndg queue doubles`, or `/ndg queue match`. Mixed rating divisions can play together.
- Open `/ndg menu` and select a mode to see its open lobbies. Each lobby shows the terrain, course seed, hole count, players queued, and preparation status. Choose a lobby to join it or use **Quick Join** to fill an open lobby first. Use **New Lobby** or select a terrain to start a separate queue. The terrain choices follow the server's course settings.
- Open **Disc Golf Guide** from the round picker or clubhouse, or use `/ndg guide`. It explains aim, timing, camera and lie controls and links to practice, your bag, and the Daily Course.
- FFA allows up to eight players and can start solo after the queue countdown. Doubles requires at least two complete teams. Parties of four form two teams; parties of two remain teammates.
- Course preparation starts while players are queued. Lobbies stay open during preparation, and a bar shows progress. Each generated hole opens with a six-second flyover. Follow the gold dots along the fairway, check the color-coded hazard cue, and look for the light above the basket. Sneak or use `/ndg skip` to skip it.
- Choose a putter, midrange, or driver from the hotbar. Right-click to start the power meter, aim, then click to release. Swap hands (normally F) to switch backhand/forehand.
- Sneak + swap hands cycles flat, hyzer, and anhyzer, including while aiming. Hyzer banks with the disc's fade, and anhyzer banks the other way.
- The short dotted aiming line shows the center of the predicted path. Use `/ndg shot preview off` to hide it.
- Use `/ndg shot stance putt` for shorter, straighter throws and finer power increments with a putter inside 15 blocks. Use `/ndg shot stance normal` when you want a full throw.
- Shallow, fast throws can skip off grass or stone. Steeper shots slide, sand stops them sooner, and a hit off the side of a tree trunk can kick the disc away. The next lie uses the final resting point.
- Use `/ndg shot stance roller` to roll a disc along the ground. Aim down an open lane. The release angle and handedness affect the curve, and Roller XP helps future shots stay on their line. Amber preview dots mark predicted contact and ground travel.
- Use `/ndg shot stance flex` for an S-shaped flight. The disc bends toward anhyzer early and fades back late. Backhand and forehand mirror the curve. Hyzer starts the fade sooner, and anhyzer holds the opening bend longer. The preview is yellow.
- Use `/ndg shot stance tomahawk` for a high overhand that pans before dropping. Use `/ndg shot stance thumber` for a steeper, shorter overhand that pans the opposite way. Both lift the release automatically. Your Minecraft main hand determines which side each pans toward. The previews are magenta for tomahawks and violet for thumbers.
- Flex, Tomahawk, and Thumber each earn their own XP when a shot resolves. Levels reduce release spread for that shot. Use `/ndg profile` to see progress.
- Use `/ndg shot timing on` to lock power with the first click and release with the second. Click while the timing bar is green for the tightest release. Accuracy skill widens the green window.
- Use your drop key (normally Q) during a round to cancel aim and keep your disc, lie, and turn. `/ndg shot cancel` also works on the practice green.
- Use `/ndg shot wind` to check the direction discs will drift on the current hole. Wind repeats for the same course seed. Server owners can disable it with `flight.wind: false`.
- Throw from your recorded lie. The compass or `/ndg lie` returns you there. Only the selected player may release a shot, and only one disc can fly at a time.
- Doubles alternates throwers after every shot and shares a lie and scorecard. Water and out-of-bounds shots add one penalty and replay the previous lie.
- The sidebar shows hole, par, distance, strokes, score, active player, remaining time, and standings. Use the hotbar book or `/ndg score` for the live scorecard. Hover over a numbered hole tile to see its par, strokes, and result. Use `/ndg score text` for the chat view.
- `/ndg concede` finishes the current hole at the stroke limit on your team's turn. `/ndg quit` forfeits the round. Disconnects forfeit the departing player's membership; remaining players continue.
- Turns time out after 90 seconds; the default maximum is 12 strokes per hole. Both are configurable. A timeout records the hole at the stroke limit.
- After the last hole, standings are announced and original inventory, location, game mode, scoreboard, and player state are restored. Generated course worlds are unloaded and removed.
- A basket plays a layered chain sound and a ripple of sparks. On an ace, the whole card gets a callout and a chime before the instant replay. Equipped score decals also play at the basket.
- Resolved throws earn skill XP. Holing out earns bonus accuracy, form, and technique XP. Longer discs give more power XP, and penalty shots give a small amount of practice XP. Use `/ndg profile` to see progress. Higher Power increases launch speed, Accuracy tightens release spread, and Form preserves speed and softens early turn on standard throws. Backhand and Forehand levels ease late fade for their respective throws.

## Match Play

Select **Match Play** in `/ndg menu` or use `/ndg queue match`. Queue solo to wait for an opponent, or queue as a party of two to play each other.

The player who finishes a hole in fewer strokes wins it. Equal strokes halve the hole. The winner tees first next, and a halved hole keeps the previous opener. `/ndg score` shows the lead, holes won, and each completed hole result.

The match ends as soon as the lead exceeds the holes left. For example, a two-hole lead with one to play ends **2 and 1**. The last hole can end the match all square. Conceding, timing out, or reaching the stroke limit awards the current hole to your opponent. Quitting after the match starts forfeits the match.

Match Play awards skill XP and credits for baskets. Ratings and clubhouse scorecards are tracked in stroke play.

## Best Shot Doubles

Select **Best Shot Doubles** in `/ndg menu` or use `/ndg queue best-shot`. The queue uses two-player teams. Queue as a pair to stay on the same team.

Both teammates throw from the same lie. Either teammate can click a choice in chat or use `/ndg lie 1` or `/ndg lie 2`. Use `/ndg lie` to show the choices again. The sidebar counts down the choice window, then selects the legal lie closest to the basket if nobody chooses. The default window is 20 seconds and servers can change it with `best-shot-choice-seconds`.

Each pair of attempts counts as one team stroke. A basket by either player completes the hole immediately. A lone legal throw becomes the next lie automatically. If both throws land in hazards, add one penalty stroke and replay the previous lie.

Both players earn skill XP for their own throws. Basket credits and clubhouse progress apply, and Best Shot has its own record cards. FFA and alternate-shot doubles update the stroke-play rating.

## Worst Shot Doubles

Select **Worst Shot Doubles** in `/ndg menu` or use `/ndg queue worst-shot`. Both teammates throw from the shared lie. The server commits the result automatically as one team stroke.

Of two legal misses, the team plays the lie farther from the basket. If one player sinks the disc and the other misses, the team plays the miss. If either disc lands in a hazard, the team returns to the previous lie with one extra penalty stroke. Both players must sink their attempts for the team to finish the hole. Worst Shot has its own Clubhouse scorecards.

## One Disc Challenge

Select **One Disc Challenge** in `/ndg menu` or use `/ndg queue one-disc`. It uses individual stroke-play scoring and can start solo or with other players.

Open the round bundle or run `/ndg bag` to pick one of the discs you packed. Clicking a disc locks it immediately for the whole round and equips it in the first hotbar slot. Each player chooses their own disc. You can change throwing technique and stance during play. The bag and `/ndg status` show your lock.

Completed rounds award basket credits, skill XP, and separate Clubhouse scorecards.

## Disc Roulette

Select **Disc Roulette** in `/ndg menu` or use `/ndg queue disc-roulette`. It uses individual stroke-play scoring and can start solo or with other players.

At the start of each throw, the server draws from your packed bag and equips the assigned disc in the first hotbar slot. Right-click it or run `/throwdisc` to aim. Canceling the meter or opening the bag keeps the draw for that throw. Consecutive draws can give you the same disc. The bundle and `/ndg status` show the current assignment.

You earn basket credits and skill XP, and clean rounds have their own Clubhouse scorecards.

## Elimination

Select **Elimination** in `/ndg menu` or run `/ndg queue elimination`. The queue needs at least two players. Whoever takes the most strokes on a hole is eliminated. The round ends when one player remains. Short courses loop back to the first tee if players are still in contention after the last hole.

When the highest stroke count is tied, only the tied players replay the hole. Everyone else is safe for that hole. If two shootouts still leave a tie, the server draws one player from the remaining tie to eliminate. A player who leaves forfeits their place.

Eliminated players return to the lobby immediately and receive the final standings and their earned basket credits when the round finishes.

## Ace Race

Select **Ace Race** in `/ndg menu` or run `/ndg queue ace-race`. Each player gets one throw from the tee on every short hole, whether their disc goes in, lands safely, or hits a hazard. A basket adds one ace. Passes and timeouts count as misses.

The most aces win. Players with the same total share their place, including first. Check the round sidebar or `/ndg score` for standings. Aces pay the usual basket credits at round completion.

Ace Race uses its own seeded layouts with tee distances of about 23–48 blocks. Set `ace-race.holes` for the round length. The default is six holes.

## Closest to Pin

Select **Closest to Pin** in `/ndg menu` or run `/ndg queue closest-to-pin`. Each player gets one throw per hole. The server measures the horizontal distance from the disc’s final position to the basket to the nearest tenth of a block. A disc that goes in scores 0.0 blocks and earns the usual basket credits.

Water, out-of-bounds throws, passes, timeouts, and flights that expire in the air count as faults. The standings rank players by fewest faults, then by lowest total distance across their scored holes. Players with equal totals and fault counts share their place. Check the sidebar or `/ndg score` for standings.

The round uses short seeded layouts and defaults to six holes. Set `closest-to-pin.holes` to change the length.

## Procedural courses

New queued courses use a fresh seed and choose from six terrain profiles by default. The seed changes hole lengths, orientation, routes, slopes, and scenery. Players in the same round share the generated course. `/ndg status` shows the terrain, seed, current hole archetype, and a difficulty estimate out of 100.

| Terrain | What to expect |
| --- | --- |
| Woodland | Rolling forest fairways with ferns, oak and spruce |
| Mountain | Alpine slopes, exposed stone and snow, and pronounced elevation changes |
| Desert | Sandstone spires, red sand, dunes, and occasional oasis carries |
| Island | Sandy shores, jungle trees, and water around the fairways |
| Nether | Crimson terrain, blackstone and basalt, and lava hazards |
| Sky | Elevated floating terrain, calcite and moss, and void carries |

Set these options under `course` in `plugins/NormalDiscGolf/config.yml` and restart the server. For example, this keeps every round in Mountain terrain while drawing a fresh layout each time.

```yaml
course:
  holes: 9
  terrain: mountain
  seed-mode: random
  terrains:
    mountain:
      elevation: 22
      fairway-width: 16
      obstacle-density: 0.40
      hazard-density: 0.25
      archetypes: [dogleg, cliffside, gap-shot, elevated-basket]
```

- **Terrain selection** supports `woodland`, `mountain`, `desert`, `island`, `nether`, `sky`, and the original `parkland`. With `terrain: random`, set `enabled-terrains` to limit the pool.
- **Seeds** use `seed-mode: random` by default. For repeatable events or testing, set `seed-mode: fixed` and enter a signed whole number under `seed`. Keep the terrain, length, and terrain settings the same when replaying a seed.
- **Terrain settings** accept `elevation` from 0 to 32 blocks, `fairway-width` from 8 to 28 blocks, and both densities from 0 to 1. An obstacle density of 0 disables scenery obstacles, including Desert arches and Sky's decorative islands. A hazard density of 0 removes generated pools and carries. Island coastlines and the Sky void are part of their terrain.
- **Hole archetypes** include `tunnel`, `dogleg`, `island-green`, `gap-shot`, `double-fairway`, `cliffside`, `guardian-green`, `air-shot`, `risk-reward`, and `elevated-basket`. Set a global `archetypes` list or override it under a terrain. The generator shuffles the enabled types and varies each hole within its type.

Ace Race and Closest to Pin keep their shorter tee distances on every terrain. Water, lava, and void landings follow the active mode’s hazard rules. Players who fall beneath a Sky course return to their lie.

Standard Woodland rounds open with **Cathedral Run**, **Fernbend**, and **Mossgate** when their archetypes are enabled. Aim through old-growth pines, play around a moss-covered boulder, and thread a pair of ruined gateposts. The seed varies the order, orientation, distances, and landmark placement. A tee board shows par, distance, and the opening shot, and gold dots mark the intended route during the skippable flyover.

Standard Desert rounds feature **Sandstone Gate**, **Painted Rim**, and **Split Mesa**. Throw under a layered sandstone arch, follow the terraced canyon, or choose either side of the mesa. Sky rounds feature **Cloud Crossing**, **Driftwood Green**, and **Sky Crown**, with floating islands in the distance and a gold guide continuing across the void carries. Background islands are out of bounds. Both terrains use themed tee pads and share their intended shot between the tee board and flyover.

Mountain showcases **Glacier Pass**, **Eagle Ridge**, and **Summit Steps** with blue ice walls, a snowy crag, and a lantern cairn. Island showcases **Wreck Lagoon**, **Lantern Cay**, and **Palm Cove** with a shipwreck, lighthouse, and palms on a dogleg. Nether showcases **Basalt Gate**, **Ember Crossing**, and **Ashen Keep** with basalt pillars, magma vents, and blackstone ruins. Each terrain opens standard rounds with its enabled showcase archetypes. The seed varies the order, orientation, terrain, and landmark placement.

The themed tee boards show the hole number, par, distance, and opening shot. Follow the gold dots in the flyover to see the intended route. On Island and Nether carries, the dots continue above the water or lava, with a separate hazard marker near the crossing. A zero obstacle density removes the landmarks. The terrain can still generate carries when hazard density is above zero.

### Reusable course profiles

In `plugins/NormalDiscGolf/config.yml`, add a profile under `course.profiles`, set `course.profile` to its name, and restart. Use letters, numbers, hyphens, or underscores in the name. The bundled config includes `desert-canyon` and `sky-islands` as starting points.

```yaml
course:
  profile: sky-quick
  profiles:
    sky-quick:
      holes: 3
      terrain: sky
      seed-mode: random
      terrains:
        sky:
          elevation: 12
          fairway-width: 18
          obstacle-density: 0.40
          hazard-density: 0.30
          archetypes: [air-shot, island-green, elevated-basket]
```

Omitted parameters inherit the base `course` settings. Profiles can also set `terrain: random` with `enabled-terrains`, or use `seed-mode: fixed` with a specific `seed` to replay the same layout. For archetypes, a profile's terrain-specific list takes precedence over its shared list. Set `course.profile: ""` to return to the base settings.

Profile terrain settings also apply to newly generated Daily and weekly courses. Those events keep their own hole counts and seeds. Ace Race and Closest to Pin keep their mode-specific hole counts and short tees. Profiles apply to procedural courses, and saved custom courses use their authored layouts.

## Player career

Run `/ndg career`, `/ndg profile`, or open **Career** in the Clubhouse to see your division, current and peak career ratings, last round rating, and skill progress. New players begin unrated in MA4. Existing profiles keep their ratings and skill levels.

| NDG career division | Rating |
| --- | --- |
| MA4 — Novice | Below 800 |
| MA3 — Recreational | 800–849 |
| MA2 — Intermediate | 850–899 |
| MA1 — Advanced | 900–949 |
| MPO — Professional | 950–999 |
| Elite Professional | 1000 and above |

FFA and Doubles award a career rating when you throw in the round and your scorecard has a basket on every hole. Round ratings account for score relative to par, course difficulty, and round length. Longer scorecards move your career rating more. The results announce rating changes and division moves.

XP comes from resolved throws in every mode. Driving, Accuracy, Form, and throwing techniques grow through regular play. Use putting stance within 15 blocks to train Putting, throw from rough, sand, or an obstructed lie to train Recovery, and play in wind to train Wind Reading. Close-range throws on your third stroke or later, or on the final hole of a multi-hole round, train Mental Focus. Skill levels cap at 5 and save with your profile.

Skill effects are normalized in rated formats by default. Everyone uses baseline effects while still earning personal XP. Server operators can change this with `career.normalize-rated-skills`. In other formats, Putting and Mental Focus widen the timing window, Recovery steadies rough-lie releases, and Wind Reading adds detail to `/ndg shot wind`.

## Arcade discs

Select **Arcade** in `/ndg menu` or use `/ndg queue arcade` to play individual stroke play with special disc loaners. Your first three packed discs remain in the hotbar. The round bag lets you switch to other discs you packed.

| Disc | Hotbar | Ability |
| --- | --- | --- |
| Phantom | 4 | Phases through the first above-ground obstacle block it strikes from the side. The next block, ground, and hazards still affect the disc. |
| Rocket | 5 | Gets one automatic speed boost on the fifth flight tick if it is still airborne. |
| Sticky | 6 | Stops on its first solid ground contact, including contacts that would normally start a skip or roll. |

Each player gets one charge for each loaner by default. Set `arcade.charges-per-disc` to a value from 0 to 9 in the server config. Charges are spent on launch and carry across holes. Aiming, previewing, and canceling leave them available. The item tooltip and `/ndg status` show what remains. Arcade rounds award skill XP and basket credits. Career ratings come from FFA and Doubles.

## Dynamic course events

Course events are shared by everyone on a hole. **Thunderstorms** bring rain where the biome supports it, safe lightning flashes, and stronger wind that changes direction during flight. **Fog** limits visibility with a vanilla Darkness effect and low mist. **Night Golf** illuminates the active tee and basket and makes flying discs glow. `/ndg status` shows the current event.

In `config.yml`, `course-events.enabled` controls the feature and `chance-percent` sets the chance of any event per hole. The default is 25% in unrated formats. Set `include-rated: true` to enable events in FFA and Doubles. Under `weights`, use 0 to exclude an event from the random draw. Course seeds reproduce the event schedule. To set an exact repeating order, use `sequence: [thunderstorm, fog, night-golf, clear]`. A nonempty sequence takes precedence over chance and weights.

`thunderstorm.wind-scale` scales storm gusts from 0 to 3. The existing `flight.wind: false` setting disables all wind. Set `thunderstorm.lightning-flashes: false` to keep the storm without the scripted flashes.

## Disc camera and instant replays

Use `/ndg camera on` during a round to follow the disc from behind, including when someone else throws. The live camera starts off for each player. Sneak or use `/ndg skip` to return to your position for the current shot. Use `/ndg camera off` to stop following future throws.

After an ace or a successful long putt in putting stance, players in the round see the recorded shot before play resumes. The replay uses the original disc and flight path. Sneak or use `/ndg skip` to leave a replay while others keep watching. Use `/ndg replay off` to opt out for the rest of the round, or `/ndg replay on` to opt back in.

Server owners can enable each feature independently under `spectating` in `config.yml`. The default `long-putt-blocks` threshold is 10 horizontal blocks, with a supported range of 1–15. Aces qualify regardless of distance.

## Custom course editor

Use `/ndg course create <id> [name]` in the permanent world where you want to build. Stand on the tee for the tee command, then move to the basket location before setting the basket.

```text
/ndg course create meadow Meadow Course
/ndg course tee 1
/ndg course basket 1
/ndg course par 1 3
/ndg course preview
/ndg course validate
/ndg course save
```

Repeat the placement and par commands for consecutive holes from 1 to 18. Choose pars from 2 to 6. Validation checks that anchors have solid ground and headroom, stay inside the world border, and leave at least five horizontal blocks from tee to basket. Use `/ndg course edit <id>` to revise a saved course and `/ndg course remove <hole>` to remove an entry from your draft.

Use `/ndg course list` to find saved courses, `/ndg course info <id>` to inspect the layout, and `/ndg course play <id>` to queue alone or as your party's leader. Custom courses run as unrated stroke play with skill XP and basket credits. The active hole's tee sign and basket are temporary displays. The course keeps the world's own terrain, time, and weather.

Saved courses load on restart from `plugins/NormalDiscGolf/custom-courses`. The source world must be loaded to validate or play. The owner can edit a saved layout. Server owners can manage editor access with `ndg.course.edit` and cross-owner editing with `ndg.course.admin`. Unsaved drafts close on disconnect.

## Round analytics

After the final standings, click **Open Round Summary** to see your score and individual stats. Use `/ndg summary` to reopen your latest completed round during the same server session, or `/ndg summary text` for the chat view.

- **Score** uses the official scorecard or format result. Doubles presents the team score with your own throwing stats.
- **Longest drive** measures your longest legal opening tee shot in horizontal blocks. Only your first throw from a tee on each hole qualifies, and an opening putt counts toward putting stats.
- **Fairway hits** count drives that finish on a marked fairway or green, including aces. Custom courses with unmarked fairways display this metric as unavailable.
- **Longest putt and putting accuracy** use successful distance and made/attempted totals in putting stance.
- **OB penalties** count actual penalty strokes charged for hazards or out of bounds. In a paired format the penalty goes to the player whose lie was used.

## Daily Course

Use `/ndg daily play` or select **Daily Course** in the round picker. Enter solo or with your party. Everyone plays the same seeded layout on a given UTC day with starter discs, equal skill bonuses, and clear skies.

Hole out on every hole to post a score. The leaderboard keeps each player's lowest stroke total and gives tied scores the same place. Open it with `/ndg daily` or right-click **Daily Course** in the picker. To see a previous day or another page, use `/ndg daily leaderboard <YYYY-MM-DD|today> [page]`.

A completed personal best saves your throw paths for that day's layout. During your next attempt, use `/ndg daily ghost on` or `off` to toggle the purple paths. Only you see them. The sidebar shows the target for the current hole. `/ndg daily ghost compare` and `/ndg score` show each hole's strokes and the running difference against the best you had when the round started. If an older best has no recording, match or beat it in a complete round to save a ghost.

The server saves each day's layout and results under `plugins/NormalDiscGolf/daily-courses`. Courses rotate at midnight UTC. A round that crosses midnight still posts to the date it was queued into. `daily-course.holes` defaults to six short par-3 holes and supports 1–18. Terrain, wind, and the stroke limit are fixed for that day when its course is first opened.

## Weekly tournaments

Open **Weekly Tournament** in the round picker or use `/ndg tournament` to see the next start and registration window. Use `/ndg tournament signup` to register and `/ndg tournament withdraw` to give up your place before the start. Sign-ups carry across server restarts. Be online, out of other rounds and queues, and ready to play at the scheduled UTC time.

The tournament uses individual stroke play with starter discs, equal skill bonuses, and short par-3 holes. Hole out on every hole to place. Equal stroke totals share a place and its prize. Open the most recent event with `/ndg tournament standings` or pass a UTC date to see that event. The standings distinguish completed cards, unfinished cards, and no-shows.

The `weekly-tournament` settings default to Saturday at 20:00 UTC, 72 hours of registration, nine holes, and a field of two to eight players. The default prizes are 100, 60, and 30 credits for places one through three. Schedule settings and prizes are saved when an event is first published. The server starts an event within five minutes of its scheduled time after downtime. If it misses that window or too few registered players are ready, it records a cancellation.

The tournament archive lives in `plugins/NormalDiscGolf/weekly-tournaments.yml`. Placement credits are delivered automatically through player profiles. A round interrupted by a server stop is recorded as canceled, and achieved results and pending prize deliveries survive restarts.

## Ranked seasons

Use `/ndg season` to see the current leaderboard, your rating, and the UTC end time. Rated FFA and Doubles
scorecards count when you participate and hole out on every hole. The season rating averages your round ratings
weighted by the holes played. Your career rating and skills carry forward.

The `ranked-seasons` settings default to 30 UTC days, three rated rounds to qualify, and 300, 180, and 100 credits
for the top three places. Tied ratings share a place and each player receives that place's prize. The first season
starts at midnight UTC on the day the feature first runs, and a round counts in the season active when its results
are finalized. Changes to season length, minimum rounds, and prizes apply when the next season opens.

Each season closes automatically. Prize credits go directly to qualifying player profiles, including offline
players. The new season starts with a fresh leaderboard. Use `/ndg season history` to find past seasons and
`/ndg season leaderboard <season number> [page]` to see their final ratings and rewards. Results and prize
delivery status are recorded in `plugins/NormalDiscGolf/ranked-seasons.yml`.

## Parkland Clubhouse

NDG builds a timber clubhouse near the overworld spawn on first startup. Open **Clubhouse** in `/ndg menu` or use `/ndg clubhouse visit` to go straight to its entrance. The original world spawn stays in place. Set `clubhouse.enabled: false` before first startup to skip building the site on a server with its own lobby.

- Right-click the **Pro Shop** sign to see the rotating rack and the next restock. Open a disc and select **Try on the Putting Green** to borrow it before buying.
- Check the **Daily Top 3** board or right-click the **Daily Course** sign for the full standings and a queue shortcut.
- Right-click **Putting Green** or run `/ndg practice` to choose any catalog disc, including custom discs. Pick the 4-, 8-, or 12-block tee with its sign or `/ndg practice tee <distance>`. Lock power, then click on green to release. Use `/throwdisc` to aim again or `/ndg practice leave` to end practice. Practice keeps your inventory and queue position and awards no XP, credits, or scores. The queue moves you into the round when it is ready.

`/ndg clubhouse` also shows the current daily challenge, your medal shelf, and personal records, with shortcuts to queue, manage your bag, and browse cosmetics.

- **Basket streaks.** Hole out on consecutive holes to build a streak. Milestones at 3, 5, and 8 get a callout, and your longest streak pays 10, 20, or 35 bonus credits when you finish. A concession, timeout, or stroke-limit hole breaks the streak.
- **Personal records.** Track your baskets, under-par holes, aces, longest basket, and best streak. Clean scorecards are tracked separately for each round length and format.
- **Daily challenges.** A new challenge starts at midnight UTC. Basket Hunt counts five baskets, Under Par counts two holes finished under par, and Back for More counts two rounds with a basket in each. The reward is paid automatically once that day when a completed round reaches the goal.
- **Medals.** Earn six persistent medals with one-time credit rewards, from First Chains to Clean Nine. The clubhouse shows what each one takes.

In doubles, teammates who throw share streak and daily progress. Personal baskets, aces, and distance records go to the player whose disc went in. Only completed rounds with a real basket count toward clubhouse progress. The extra credits use the same balance as the shop.

## Disc shop

Open `/ndg shop` between rounds. Click a disc to inspect its speed, glide, turn, fade, and price. Confirm the purchase in the same window, then use **Pack for next round** or open **Your Bag**. The same rack shows what's in stock, out of stock, owned, or packed.

The three starter discs are free. The featured rack offers one unlockable putter, one midrange, and one driver at a time. It restocks daily at midnight UTC by default. Owned discs stay available for packing after the rack rotates. Set `disc-shop.rotation-hours: 0` to keep the entire catalog in stock. Commands `/ndg shop buy <disc>` and `/ndg shop pack <disc>` offer the same purchase and packing actions.

By default, completing a round awards 10 credits for each hole successfully finished in the basket. An 18-hole round with every basket made pays 180 credits. Doubles partners each earn the full reward for their team's baskets. Conceded, timed out, and stroke-limit holes earn zero credits, and leaving or canceling the round forfeits the reward. Set `disc-shop.credits-per-basket` to adjust the payout. Balances, unlocks, and loadouts are saved in player profiles.

## Disc bag

Open `/ndg bag` between rounds. You start with five slots and three starter discs. Click an owned disc to pack it. Left-click a packed disc to remove it or right-click it to move it first. You can pack several discs of the same type and must keep at least one disc in the bag. Capacity upgrades cost 180 credits for seven slots and 320 credits for nine slots. Existing profiles carry their equipped discs into the new bag.

Rounds use the bag you packed when the round started. The first six discs fit on the hotbar. Open the bundle in slot 8 or use `/ndg bag` to select any packed disc, including the ones beyond the hotbar. Right-click the selected disc to start the power meter. The compass and scorecard keep their hotbar slots, and sneak still skips flyovers.

## Score decals

Open `/ndg decals` between rounds or select **Score Decals** in `/ndg shop`. Right-click an effect to preview it privately. Left-click to unlock it with round credits, then left-click again to equip it. Choose **No Decal** to clear your selection. Gold Ring costs 60 credits, Aurora Spiral costs 90, and Ember Bloom costs 120.

When a throw sinks in the basket, the thrower's equipped effect appears above it for nearby players. In doubles, the teammate who makes the shot provides the decal. Each effect uses vanilla particles and works without the disc resource pack. Unlocks and your equipped decal are saved alongside your discs and credits.

## Disc model resource pack

The plugin builds `plugins/NormalDiscGolf/NormalDiscGolfPack.zip` at startup. With an empty `resource-pack.url`, the built-in server automatically offers a required pack to players who connect from the server machine. In Minecraft, connect to `127.0.0.1` and accept the pack or enable Server Resource Packs for that server entry. The local HTTP port defaults to 8088. Change `resource-pack.port` if that port is in use. Set `resource-pack.local-required: false` to make the local pack optional or `resource-pack.local-auto: false` to disable automatic local delivery. Restart after changing pack settings.

Each disc has its own model, rim profile, and colors. With optional packs, the shop and bag use labeled vanilla icons while the pack is unavailable. **Disc Models** in the shop and `/ndg pack status` show when the pack is offered, downloading, applying, or active. If a download or load fails, click **Disc Models** or run `/ndg pack retry`. Open menus and round hotbar items update after it loads.

The pack adds textured frames and pixel-art controls to the round picker, lobbies, clubhouse, shop, bags, putting green, decals, guide, scorecard, and round summary. It also adds matching badges to the live round sidebar. The menus and sidebar work with vanilla icons and text while the pack is unavailable. Set `resource-pack.menu-textures: false` and restart to use the vanilla interface with custom disc models.

For remote delivery, set `resource-pack.url` to a publicly reachable URL, such as `https://your-host.example/ndg/pack.zip`. The built-in HTTP server serves `/ndg/pack.zip` on port 8088 when a URL is configured; route that port or use a reverse proxy. To host the ZIP elsewhere, set `resource-pack.serve: false` and point the URL at the hosted file. No public address or firewall configuration is inferred automatically.

Set `resource-pack.required: true` to require a configured remote pack at login. Required-pack players must finish loading it before queueing. With optional packs, labeled vanilla items remain playable and flight models use vanilla items if anyone viewing the course lacks the pack.

## Add your own discs

1. Start the server once to create `plugins/NormalDiscGolf/custom-discs/_example`. Copy that folder to a new folder alongside it, such as `custom-discs/riverstone`. Use a unique lowercase ID made of letters, numbers and hyphens. Keep that ID if you change the disc's name, stats, or design so owners retain their unlocks.
2. Edit `disc.yml` to set the display name, type, price, and flight stats. Set the price to `0` to make the disc available to everyone. For a generated model, pick `classic`, `rounded`, `dome`, `puddle`, `beveled`, or `low-profile` under `appearance.shape` and set the three quoted hex colors.
3. For your own geometry, export a **Java Block/Item** JSON model from Blockbench and save it as `model.json` next to `disc.yml`. The example includes a `model.json.example` you can rename to try an imported model using vanilla textures. For an export with custom textures, copy its PNGs into a `textures` subfolder. A reference like `my-pack:item/riverstone` looks for `textures/item/riverstone.png` or `textures/riverstone.png`. Vanilla references such as `minecraft:block/cyan_concrete` work as well. Use a self-contained model or a vanilla parent like `minecraft:item/generated`. [Blockbench format guide](https://blockbench.net/wiki/blockbench/formats/)
4. Restart the server. The disc appears on the **Custom Discs** shop page, joins the stock rotation, and can be packed and thrown like any other disc. The plugin rebuilds the ZIP and its download hash automatically. If you host the pack yourself, upload the new ZIP before players reconnect.

The server log names invalid definitions and missing or unreadable model textures. A valid disc with bad artwork uses its generated shape and colors. If a disc folder is temporarily removed, its unlock stays in the player's profile. Restore the same folder ID and repack it to use it again.

## Hosted 1.21.8 playtest

Use a separate Paper 1.21.8 server with Java 21. Start it in online mode and enable the whitelist.
Add the test players before opening the game address. Install the NDG gameplay JAR and use a fresh plugin data
directory for the playtest. Set `course.holes: 18` and leave the preview enabled. A fixed seed lets each group
reproduce a reported hole. Record the terrain, seed, hole, and active mode from `/ndg status`.

Upload the generated `plugins/NormalDiscGolf/NormalDiscGolfPack.zip` and set `resource-pack.url` to the exact
public HTTPS download URL. Set `resource-pack.serve: false` for an externally hosted ZIP, and set
`resource-pack.required: true` to test the join gate. Have one tester download the URL and join from outside the
server network. Check the pack prompt, the custom hotbar and shop models, and whether declining the required pack
prevents queueing. Repeat with `required: false` to check the vanilla icons and `/ndg pack retry`.
An unavailable optional pack leaves gameplay open with labeled vanilla icons.

Play all 18 holes into the basket in a multiplayer party. Check tee direction and route legibility, hazard
recovery, full and skipped flyovers, the disc-follow camera in flight, replay and skip behavior, and restoration
of the lie after each camera. Test the hotbar, swap-hands and sneak shortcuts, aim preview, timing, and putting.
At the end, compare scorecards, summaries, and balances, then reconnect and check the earned progress.

## Verification

`./gradlew build` runs the dependency-free regression suite: scorecards, hazard totals, duplicate scoring, full-round totals, lane geometry, and 12 seeded course configurations.

The separate real-server harness checks generated terrain and tee anchors, basket-specific swept collision, party membership, doubles grouping, lie validation, overlapping shots, stale callbacks, alternate-shot rotation, hole progression, resource-pack JSON, SHA-1 and HTTP delivery, and world cleanup. Run:

`powershell -File scripts/Smoke-Test.ps1 -ServerJar C:/path/to/spigot.jar -WorldEditJar C:/path/to/worldedit.jar -EulaFile C:/path/to/accepted/eula.txt`

This runs a loopback-only test server under `build/smoke-server`, then stops it automatically. The test-only `NDGSmokeChecks` plugin is built separately and shuts down its server after verification. Do not install that test plugin on a gameplay server.

The rule harness passed on Paper 1.21.8 build 60 with WorldEdit 7.3.19. A vanilla Minecraft Java 1.21.8 client also verified resource-pack loading, disc models, the power meter and release controls, technique switching, return to lie, the fairway flyover and its tee-facing finish, preview skipping, and restoration after quitting. Preview paths have bounded pitch and rounded, distance-paced movement; waiting players stand clear of the active thrower.

The clubhouse network harness in `scripts/multiplayer/clubhouse-flow.cjs` passed on Paper 1.21.8 build 60 with only the NDG jar. Two protocol clients verified the local pack download, clickable shop and Daily signs, a practice basket with a loaner disc while queued, and the handoff into Match Play. The saved clubhouse was reused after restart. In a separate two-hole Daily fixture, a completed score appeared on the physical board while a second client watched.

The polish harness in `scripts/multiplayer/polish-flow.cjs` verified guide navigation, aim cancellation, stable sidebar updates, and a complete two-player FFA on a two-hole Desert course. The required-pack harness also checked pack status messages, cache revalidation, and queue access after a successful retry.

The menu harness in `scripts/multiplayer/textured-menu-flow.cjs` checks textured navigation, existing disc models, and live updates when a player loads or removes the optional resource pack.

The round interface harness in `scripts/multiplayer/textured-round-flow.cjs` checks the sidebar, numbered hole tiles, and final analytics with one pack-enabled client and one vanilla client during a complete two-player round.

Verified network runs completed 18-hole FFA with two clients (36 throws) and 18-hole doubles with four clients (72 throws). After the final camera-transition cleanup, a three-hole FFA regression also passed with client-side pitch assertions, and the disconnect/skip check passed with an explicit preview-title reset assertion. The graphical client confirmed the final skip clears the title and returns to a course-facing tee position.

Network multiplayer checks live in `scripts/multiplayer`. With Node 22 or newer, run `npm ci --prefix scripts/multiplayer`, then run these commands from the repository root against an isolated 1.21.8 server on `127.0.0.1:25569`:

```text
node scripts/multiplayer/round-flow.cjs ffa 18
node scripts/multiplayer/round-flow.cjs doubles 18
node scripts/multiplayer/disconnect-flow.cjs
```

Match the server's configured hole count to the final argument. Keep the six-second preview enabled and allow enough turn time for automation. The clients use offline test identities with ordinary player permissions; use a loopback-only test server. Run FFA and doubles sequentially because they share client names. JSON transcripts are written under `build/multiplayer-*.json`.

For a server using automatic local delivery, add `--expect-local-pack` to verify the required login pack and its download hash. Add `--expect-skill-xp` with fresh test profiles to check that each resolved shot earns one XP award. Add `--expect-shot-controls` to check private preview particles, the sneak + swap angle shortcut, and a clean release with the two-click timing meter. The round script reads `NDG_TEST_PORT` if set.

Set `NDG_TEST_PACK_URL` instead of `--expect-local-pack` to check the exact configured pack URL, the required
prompt flag, and the download hash while playing on the loopback fixture.

On the one-hole course with seed 41, add `--expect-roller` to verify a live ground roll and its XP award for each player. Run `node scripts/multiplayer/round-flow.cjs doubles 1 --expect-trick-shots` on the same fixture to check all three trick shots, including private previews, live trajectories and XP.

For the seed-41 Parkland fixtures, set `course.terrain: parkland`, `course.seed-mode: fixed`, and `course.seed: 41`. For Match Play, use three holes with `queue-countdown-seconds: 3`, `flight.wind: false`, and `resource-pack.enabled: false`. Run `node scripts/multiplayer/match-flow.cjs` to exercise solo matchmaking, the round picker, party matchmaking, real baskets, a halved hole, a final draw, an early clinch, and a forfeit.

For Best Shot Doubles, use the same settings with one hole and `best-shot-choice-seconds: 8`. Run `node scripts/multiplayer/best-shot-flow.cjs` to exercise four-player matchmaking, manual and automatic lie choices, baskets, and round restoration. With a three-hole fixture, run `node scripts/multiplayer/round-flow.cjs best-shot 3 --expect-skill-xp` to check choices and turn handling across hole transitions.

For Worst Shot Doubles, run `node scripts/multiplayer/worst-shot-flow.cjs` on the one-hole seed-41 fixture. The script checks automatic lie selection, a real basket paired with a miss, two successful attempts to finish a hole, and round restoration.

Run `node scripts/multiplayer/one-disc-flow.cjs` on the three-hole seed-41 fixture. Two players choose different discs, hole out on the opening hole, and script the remaining holes to check that the lock survives hole transitions, final standings, rewards, and bag restoration.

Run `node scripts/multiplayer/disc-roulette-flow.cjs` on the three-hole seed-41 fixture. Two players pack different bags, verify one draw per throw, and exercise the round picker, draw enforcement, hole transitions, final standings, rewards, and bag restoration.

Run `node scripts/multiplayer/elimination-flow.cjs` on the two-hole seed-41 fixture. It checks the minimum queue size, a three-player shootout, a final played hole, repeated ties, and forfeits.

For Ace Race, set `ace-race.holes: 3`, `course.seed: 41`, `flight.wind: false`, `turn-timeout-seconds: 35`, and `disc-shop.credits-per-basket: 10` on the loopback fixture. Run `node scripts/multiplayer/ace-race-flow.cjs` to check one attempt per hole, a shared first place, a clear winner, exact ace rewards, and lobby restoration.

If your fixture uses a different turn timeout, set `NDG_TEST_TURN_SECONDS` to the same number.

For Closest to Pin, use the same fixture with `closest-to-pin.holes: 3`. Run `node scripts/multiplayer/closest-to-pin-flow.cjs` to check measured landings, zero-distance baskets, equal totals, how faults affect placement, and lobby restoration.

For procedural courses, set `course.terrain: sky`, `course.seed-mode: random`, `course.terrains.sky.archetypes: [air-shot, dogleg, elevated-basket]`, and `closest-to-pin.holes: 3`. Run `node scripts/multiplayer/procedural-course-flow.cjs` to play two courses in the same environment and compare their generated seeds and tee locations.

For the Woodland showcase, use `course.terrain: woodland`, `course.holes: 3`, `course.seed-mode: fixed`, and `course.seed: 2026` with the default archetypes. Run `node scripts/multiplayer/round-flow.cjs ffa 3 --expect-woodland-visuals --expect-shot-controls --expect-skill-xp`. Both clients receive the three tee boards, shot tips, moving flyovers, and gold route markers.

For Desert and Sky, use the same three-hole fixed-seed setup with the corresponding terrain and run `node scripts/multiplayer/round-flow.cjs ffa 3 --expect-desert-visuals` or `--expect-sky-visuals`. Leave obstacle and hazard densities above zero. For Sky, also set `NDG_EXPECT_FLYOVER_HAZARD=Void` to assert the hazard cue and marker. The server smoke test checks the solid sandstone crown, clearance underneath, and actual floating island blocks.

For Mountain, Island, and Nether, use that same fixture with `--expect-mountain-visuals`, `--expect-island-visuals`, or `--expect-nether-visuals`. Set `NDG_EXPECT_FLYOVER_HAZARD=Water` for Island or `Lava` for Nether. The harness checks tee boards, route shape, camera clearance, and gold markers above the carries. The smoke test verifies the landmark materials in the generated chunks.

For flyover cues, use a one-hole Island Green on Island terrain with hazard density above zero. Set `NDG_EXPECT_FLYOVER_HAZARD=Water` and run `node scripts/multiplayer/round-flow.cjs ffa 1` to check the text cue and three distinct markers. The same check accepts `Lava` on Nether and `Void` on Sky when those holes contain a carry or a drop-off along the route.

For basket feedback, enable replays and use a flat world with custom course creation enabled. Run `node scripts/multiplayer/basket-celebration-flow.cjs` to build a short test hole and check an ace, an ordinary basket, the replay, and who receives each cue.

For careers, use the one-hole fixed Parkland fixture with `closest-to-pin.holes: 1` and set `NDG_CAREER_PROFILE_DIR` to the loopback server’s `plugins/NormalDiscGolf/player-profiles` directory. Run `node scripts/multiplayer/career-flow.cjs` to verify shot XP, level-ups, promotion, a rating drop, and conceded and arcade rounds. Restart the test server and run the same script with `--verify-restart` to compare both profiles with the saved report.

For Arcade, use the two-hole fixed Parkland fixture with `arcade.charges-per-disc: 1` and `turn-timeout-seconds: 150`. Run `node scripts/multiplayer/arcade-flow.cjs` and enter the two commands it prints after `FIXTURE` in the isolated server console. The commands place single-block obstacles for the Phantom test in the temporary course. The script checks all three effects, charge limits and resets, isolation from regular stroke play, and round rewards.

For course events, use four holes on the fixed Parkland fixture with wind enabled, `course-events.sequence: [thunderstorm, fog, night-golf, clear]`, `include-rated: false`, and lightning flashes enabled. Run `node scripts/multiplayer/course-events-flow.cjs` to verify the conditions and throws through a four-hole Arcade round and the rated FFA opt-out.

For spectating and replays, use the one-hole fixed Parkland fixture with wind and course events disabled, both spectating features enabled, and `spectating.long-putt-blocks: 10`. Run `node scripts/multiplayer/spectating-flow.cjs` to check live camera movement and skip controls, actual ace and long putt replays, opt-out, and player restoration on completion and forfeit.

For the course editor, use a flat permanent world with open space at spawn, wind disabled, and a short queue countdown. Run `node scripts/multiplayer/custom-course-flow.cjs` to create, validate, save, edit, and play a two-hole course. Restart the server and run the same script with `--after-restart` to play the saved layout again. Set `NDG_TEST_CUSTOM_COURSE` to an unused course ID for each new editor run and keep the same ID for its restart check.

For round analytics, use the one-hole fixed Parkland fixture with wind, course events, and instant replays disabled. Run `node scripts/multiplayer/analytics-flow.cjs` and enter the `FIXTURE` command it prints in the isolated server console. It adds water to the temporary course to produce a real penalty. The two clients check score, a legal drive, a fairway hit, a missed and made putt, and reopening their separate summaries after the round.

For the Daily Course, use a fresh loopback fixture and set `daily-course.holes: 1`, `course.terrain: parkland`, `flight.wind: false`, and `spectating.replays: false` before creating today's layout. Run `node scripts/multiplayer/daily-course-flow.cjs` to compare two separately generated copies, improve a daily best, and exercise ties and a concession. Restart Paper and run the same script with `--after-restart` to verify the saved board.

For Daily ghosts, use a fresh two-hole Desert fixture with `tunnel` and `gap-shot` archetypes, zero elevation, obstacle density, and hazard density, and wind and instant replays disabled. Run `node scripts/multiplayer/daily-ghost-flow.cjs` to compare actual throws with the private paths and check both players' hole-by-hole scores. Restart the server and rerun with `--after-restart` to verify the saved bests.

For weekly tournaments, use a fresh loopback fixture with one hole, four places, prizes `[100, 60, 30]`, Parkland terrain, and wind and replays disabled. Configure the UTC weekday and time a few minutes ahead, and set `NDG_WEEKLY_START` to the matching ISO timestamp such as `2026-11-26T23:55:00Z`. Run `node scripts/multiplayer/weekly-tournament-flow.cjs` for registration, scheduled start, play, standings, payouts, and the next event. Run it with `--after-restart` to verify archived standings and balances.

For ranked seasons, use the one-hole fixed Parkland fixture with wind, course events, and replays disabled. Seed an
empty Season 1 archive with two rounds to qualify, prizes `[300, 180, 100]`, and an end time a few minutes away.
Set `NDG_SEASON_END` to that UTC timestamp and `NDG_SEASON_PROFILE_DIR` to the test server's profile directory.
Set `ranked-seasons.minimum-rounds: 2` for the next season. Run `node scripts/multiplayer/ranked-season-flow.cjs`
to qualify through rated rounds, wait for rollover, and verify online and offline payouts. Run it with
`--after-restart` to verify current and archived standings and both prize balances.

Each full-round check queues a party, makes a real networked throw per player per hole, rejects a second throw during flight, then concedes to advance predictably through every hole. It checks final standings and restores each client's inventory, game mode, and location. Basket completion and swept collision are covered separately by the real-server rule harness. The disconnect check exercises the 1.21.8 sneak input to skip previews, out-of-turn rejection, departure during flight, continued play, and restoration after quitting. These checks do not claim that automated clients skillfully played every hole into the basket.

`Smoke-Test.ps1` accepts `-ServerPort` and `-ResourcePackPort` overrides when a separate local playtest server is running.

Run `node scripts/multiplayer/release-player-flow.cjs` on a one-hole loopback fixture with all shop stock enabled,
an optional pack, and an explicit non-loopback URL given in `NDG_TEST_PACK_URL`. This covers pack delivery and SHA-1,
decline and retry, live shop model refresh, invalid shop and bag actions, duplicate queues, out-of-turn throws, and
concessions. To exercise optional delivery failure, configure an invalid built-in HTTP port and run it with
`--unavailable-pack`. Use `--checkout` with `NDG_TEST_PROFILE_DIR` pointing to the fixture’s profile directory
to check disc purchase and packing, bag upgrades, duplicate charges, and decal persistence. Use `--required-pack`
with the configured pack URL to check queue eligibility before and after a retry.
