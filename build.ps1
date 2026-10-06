$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceRoot = Join-Path $projectRoot 'src\main\java'
$outputRoot = Join-Path $projectRoot 'out\main'
$distRoot = Join-Path $projectRoot 'dist'
foreach ($requiredTool in @('java', 'javac')) {
    if (-not (Get-Command $requiredTool -ErrorAction SilentlyContinue)) {
        throw "Missing $requiredTool. Install JDK 17 or newer and configure PATH."
    }
}
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
if ($jarCommand) {
    $jarTool = $jarCommand.Source
} else {
    $javaProbeInfo = New-Object System.Diagnostics.ProcessStartInfo
    $javaProbeInfo.FileName = (Get-Command java).Source
    $javaProbeInfo.Arguments = '-XshowSettings:properties -version'
    $javaProbeInfo.UseShellExecute = $false
    $javaProbeInfo.CreateNoWindow = $true
    $javaProbeInfo.RedirectStandardError = $true
    $javaProbeInfo.RedirectStandardOutput = $true
    $javaProbe = [System.Diagnostics.Process]::Start($javaProbeInfo)
    $javaSettings = $javaProbe.StandardError.ReadToEnd()
    $javaProbe.WaitForExit()
    $probeCode = $javaProbe.ExitCode
    $javaProbe.Dispose()
    if ($probeCode -ne 0) { throw 'Cannot inspect the Java runtime.' }
    $runtimeHomeLine = $javaSettings -split "`r?`n" | Where-Object { $_ -match '^\s*java.home\s*=' } | Select-Object -First 1
    $runtimeHome = ($runtimeHomeLine -replace '^\s*java.home\s*=\s*', '').Trim()
    if (-not $runtimeHome) { throw 'Cannot locate the Java runtime home.' }
    $jarTool = Join-Path $runtimeHome 'bin\jar.exe'
    if (-not (Test-Path -LiteralPath $jarTool)) { throw 'The runtime has no jar tool. Install a full JDK 17+.' }
}
New-Item -ItemType Directory -Force -Path $outputRoot, $distRoot | Out-Null
$sources = Get-ChildItem -Path $sourceRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
if (-not $sources) { throw 'No Java source files were found.' }
javac --release 17 -encoding UTF-8 -d $outputRoot $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$jarFile = Join-Path $distRoot 'MyWorld3D.jar'
& $jarTool --create --file $jarFile --main-class com.myworld3d.GameLauncher -C $outputRoot .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Write-Output "Built $jarFile (requires Java 17+)"
