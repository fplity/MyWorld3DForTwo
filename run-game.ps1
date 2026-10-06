$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceRoot = Join-Path $projectRoot 'src\main\java'
$outputRoot = Join-Path $projectRoot 'out\main'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$sources = Get-ChildItem -Path $sourceRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
if (-not $sources) { throw 'No Java source files were found.' }
javac --release 17 -encoding UTF-8 -d $outputRoot $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Push-Location $projectRoot
try {
    java '-Dfile.encoding=UTF-8' -cp $outputRoot com.myworld3d.GameLauncher
    if ($LASTEXITCODE -ne 0) { throw 'The game process exited with an error.' }
} finally {
    Pop-Location
}
