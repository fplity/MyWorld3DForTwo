$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $projectRoot 'build.ps1')
$jarFile = Join-Path $projectRoot 'dist\MyWorld3D.jar'
Push-Location $projectRoot
try {
    java '-Dfile.encoding=UTF-8' -jar $jarFile
    if ($LASTEXITCODE -ne 0) { throw 'The game process exited with an error.' }
} finally {
    Pop-Location
}
