$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $projectRoot 'test.ps1')
$mainOut = Join-Path $projectRoot 'out\main'
$testOut = Join-Path $projectRoot 'out\test'
$evidence = Join-Path $projectRoot '.codex\team-runs\2026-10-06-repair-archive\evidence'
java '-Dfile.encoding=UTF-8' -cp "$mainOut;$testOut" com.myworld3d.DesktopSmoke $evidence
if ($LASTEXITCODE -ne 0) { throw 'Desktop smoke test failed.' }
