$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mainRoot = Join-Path $projectRoot 'src\main\java'
$testRoot = Join-Path $projectRoot 'src\test\java'
$mainOut = Join-Path $projectRoot 'out\main'
$testOut = Join-Path $projectRoot 'out\test'
New-Item -ItemType Directory -Force -Path $mainOut, $testOut | Out-Null
$mainSources = Get-ChildItem -Path $mainRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Path $testRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
javac --release 17 -encoding UTF-8 -d $mainOut $mainSources
if ($LASTEXITCODE -ne 0) { throw 'Main source compilation failed.' }
javac --release 17 -encoding UTF-8 -cp $mainOut -d $testOut $testSources
if ($LASTEXITCODE -ne 0) { throw 'Test source compilation failed.' }
Push-Location $projectRoot
try {
    java -ea '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -cp "$mainOut;$testOut" com.myworld3d.GameTests
    if ($LASTEXITCODE -ne 0) { throw 'Game tests failed.' }
} finally {
    Pop-Location
}
