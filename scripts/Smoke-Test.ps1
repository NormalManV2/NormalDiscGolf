param(
    [Parameter(Mandatory=$true)][string]$ServerJar,
    [Parameter(Mandatory=$true)][string]$WorldEditJar,
    [Parameter(Mandatory=$true)][string]$EulaFile,
    [string]$GradleUserHome,
    [int]$ServerPort = 25569,
    [int]$ResourcePackPort = 18088
)
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$testPath = Join-Path $workspacePath 'build/smoke-server'
if (-not (Select-String -LiteralPath $EulaFile -Pattern '^eula=true$' -Quiet)) {
    throw 'Supply an existing eula.txt for a server whose EULA you have accepted.'
}
Push-Location $workspacePath
try {
    $gradleArguments = @('build', 'smokeTestJar')
    if ($GradleUserHome) { $gradleArguments += @('--gradle-user-home', $GradleUserHome) }
    $nativePreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & .\gradlew.bat @gradleArguments; $buildExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $nativePreference }
    if ($buildExit -ne 0) { throw 'Build failed.' }
    New-Item -ItemType Directory -Path (Join-Path $testPath 'plugins') -Force | Out-Null
    Copy-Item -LiteralPath $ServerJar -Destination (Join-Path $testPath 'spigot.jar')
    Copy-Item -LiteralPath $WorldEditJar -Destination (Join-Path $testPath 'plugins/worldedit.jar')
    Copy-Item -LiteralPath $EulaFile -Destination (Join-Path $testPath 'eula.txt')
    Copy-Item -LiteralPath (Join-Path $workspacePath 'build/libs/NormalDiscGolf-1.0-SNAPSHOT-all.jar') -Destination (Join-Path $testPath 'plugins/NormalDiscGolf.jar')
    Copy-Item -LiteralPath (Join-Path $workspacePath 'build/libs/NDGSmokeChecks-1.0-SNAPSHOT.jar') -Destination (Join-Path $testPath 'plugins/NDGSmokeChecks.jar')
    @'
server-ip=127.0.0.1
server-port=25569
online-mode=false
view-distance=3
simulation-distance=3
spawn-protection=0
generate-structures=false
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
'@ -replace 'server-port=25569', "server-port=$ServerPort" | Set-Content -LiteralPath (Join-Path $testPath 'server.properties') -Encoding ASCII
    New-Item -ItemType Directory -Path (Join-Path $testPath 'plugins/NormalDiscGolf') -Force | Out-Null
    @'
resource-pack:
  enabled: true
  url: ""
  serve: true
  port: 18088
  required: false
'@ -replace '18088', "$ResourcePackPort" | Set-Content -LiteralPath (Join-Path $testPath 'plugins/NormalDiscGolf/config.yml') -Encoding ASCII
    Push-Location $testPath
    try {
        $ErrorActionPreference = 'Continue'
        & java -Xms512M -Xmx1536M -DIReallyKnowWhatIAmDoingISwear -jar spigot.jar nogui
        $serverExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativePreference; Pop-Location }
    if ($serverExit -ne 0) { throw 'Test server failed to start or exited abnormally.' }
    if (-not (Select-String -LiteralPath (Join-Path $testPath 'logs/latest.log') -Pattern 'NDG_SMOKE_PASS' -Quiet)) {
        throw 'Server integration checks failed; inspect build/smoke-server/logs/latest.log.'
    }
} finally { Pop-Location }
