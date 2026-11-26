# NormalDiscGolf

A multiplayer Minecraft disc-golf plugin with recreational FFA and alternate-shot doubles, generated parkland courses, and complete scorecards.

## Build and install

Use Java 21 and run `./gradlew build`. Install `build/libs/NormalDiscGolf-1.0-SNAPSHOT-all.jar` together with WorldEdit on a Spigot-compatible server. The compile target and resource pack are Minecraft 1.21.8. Restart the server after installing.

The default round has 18 holes. Set `course.holes: 3` in `plugins/NormalDiscGolf/config.yml` for a shorter round. The supported range is 1–18. The seed controls repeatable ground layout. Settings apply to subsequently created rounds; restart after changing configuration.

## Play

- Use `/ndg party invite <player>` and have friends run `/ndg party accept`. The leader runs `/ndg queue ffa` or `/ndg queue doubles`. Mixed rating divisions can play together.
- FFA allows up to eight players and can start solo after the queue countdown. Doubles requires at least two complete teams. Parties of four form two teams; parties of two remain teammates.
- A preparation bar shows course progress. Each hole opens with a six-second flyover; sneak or use `/ndg skip` to skip it.
- Choose a putter, midrange, or driver from the hotbar. Right-click to start the power meter, aim, then click to release. Swap hands (normally F) to switch backhand/forehand.
- Throw from your recorded lie. The compass or `/ndg lie` returns you there. Only the selected player may release a shot, and only one disc can fly at a time.
- Doubles alternates throwers after every shot and shares a lie and scorecard. Water and out-of-bounds shots add one penalty and replay the previous lie.
- The sidebar shows hole, par, distance, strokes, score, active player, remaining time, and standings. The book or `/ndg score` shows the full scorecard.
- `/ndg concede` finishes the current hole at the stroke limit on your team's turn. `/ndg quit` forfeits the round. Disconnects forfeit the departing player's membership; remaining players continue.
- Turns time out after 90 seconds; the default maximum is 12 strokes per hole. Both are configurable. A timeout records the hole at the stroke limit.
- After the last hole, standings are announced and original inventory, location, game mode, scoreboard, and player state are restored. Generated course worlds are unloaded and removed.

## Optional disc models

The plugin builds `plugins/NormalDiscGolf/NormalDiscGolfPack.zip` at startup. Labeled vanilla items remain playable without the pack.

For remote delivery, set `resource-pack.url` to a publicly reachable URL, such as `https://your-host.example/ndg/pack.zip`. The built-in HTTP server serves `/ndg/pack.zip` on port 8088 when a URL is configured; route that port or use a reverse proxy. To host the ZIP elsewhere, set `resource-pack.serve: false` and point the URL at the hosted file. No public address or firewall configuration is inferred automatically.

Set `resource-pack.required: true` only when delivery is configured. Required-pack players must finish loading it before queueing. With optional packs, flight models use vanilla items if anyone viewing the course lacks the pack.

## Verification

`./gradlew build` runs the dependency-free regression suite: scorecards, hazard totals, duplicate scoring, full-round totals, lane geometry, and 12 seeded course configurations.

The separate real-server harness checks generated terrain and tee anchors, basket-specific swept collision, party membership, doubles grouping, lie validation, overlapping shots, stale callbacks, alternate-shot rotation, hole progression, resource-pack JSON, SHA-1 and HTTP delivery, and world cleanup. Run:

`powershell -File scripts/Smoke-Test.ps1 -ServerJar C:/path/to/spigot.jar -WorldEditJar C:/path/to/worldedit.jar -EulaFile C:/path/to/accepted/eula.txt`

This runs a loopback-only test server under `build/smoke-server`, then stops it automatically. The test-only `NDGSmokeChecks` plugin is built separately and shuts down its server after verification. Do not install that test plugin on a gameplay server.

The rule harness passed on Paper 1.21.8 build 60 with WorldEdit 7.3.19. A vanilla Minecraft Java 1.21.8 client also verified resource-pack loading, disc models, the power meter and release controls, technique switching, return to lie, the fairway flyover and its tee-facing finish, preview skipping, and restoration after quitting. Preview paths have bounded pitch and rounded, distance-paced movement; waiting players stand clear of the active thrower.

Verified network runs completed 18-hole FFA with two clients (36 throws) and 18-hole doubles with four clients (72 throws). After the final camera-transition cleanup, a three-hole FFA regression also passed with client-side pitch assertions, and the disconnect/skip check passed with an explicit preview-title reset assertion. The graphical client confirmed the final skip clears the title and returns to a course-facing tee position.

Network multiplayer checks live in `scripts/multiplayer`. With Node 22 or newer, run `npm ci --prefix scripts/multiplayer`, then run these commands from the repository root against an isolated 1.21.8 server on `127.0.0.1:25569`:

```text
node scripts/multiplayer/round-flow.cjs ffa 18
node scripts/multiplayer/round-flow.cjs doubles 18
node scripts/multiplayer/disconnect-flow.cjs
```

Match the server's configured hole count to the final argument. Keep the six-second preview enabled and allow enough turn time for automation. The clients use offline test identities with ordinary player permissions; use a loopback-only test server. Run FFA and doubles sequentially because they share client names. JSON transcripts are written under `build/multiplayer-*.json`.

Each full-round check queues a party, makes a real networked throw per player per hole, rejects a second throw during flight, then concedes to advance predictably through every hole. It checks final standings and restores each client's inventory, game mode, and location. Basket completion and swept collision are covered separately by the real-server rule harness. The disconnect check exercises the 1.21.8 sneak input to skip previews, out-of-turn rejection, departure during flight, continued play, and restoration after quitting. These checks do not claim that automated clients skillfully played every hole into the basket.

`Smoke-Test.ps1` accepts `-ServerPort` and `-ResourcePackPort` overrides when a separate local playtest server is running.
