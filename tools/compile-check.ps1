# Local javac check for the two source sets (no Gradle, no network).
# Rebuilt 2026-10-29. Mirrors tools/compile-check.sh: MC jars from
# D:/Gradle/caches/fabric-loom/26.3, dependency jars from D:/Gradle/caches/modules-2.
#
# Output (identical to compile-check.sh):
#   [main] sources=N / errors=N / classes=N / log=...
#   --- first errors (up to 40) ---
#   [client] ...
#   [summary] main errors=N classes=N | client errors=N classes=N
# Exit: 0 no errors, 1 errors, 2 setup problem.
param([string]$Pass = "both")
$ErrorActionPreference = "Continue"

if ($env:COMPILE_CHECK_ROOT) { $T = $env:COMPILE_CHECK_ROOT } else { $T = Split-Path -Parent $PSScriptRoot }
if (-not (Test-Path (Join-Path $T "build.gradle"))) {
    Write-Error "compile-check: not the project root (no build.gradle): $T"
    exit 2
}
Set-Location $T

# --- toolchain ----------------------------------------------------------
$JAVAC = $null
$candidates = @("C:\Program Files\Java\jdk-25\bin\javac.exe")
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\javac.exe") }
$cmdJavac = Get-Command javac -ErrorAction SilentlyContinue
if ($cmdJavac) { $candidates += $cmdJavac.Source }
foreach ($cand in $candidates) {
    if (-not (Test-Path $cand)) { continue }
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $cand
        $psi.Arguments = "-version"
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $ver = ($proc.StandardOutput.ReadToEnd() + " " + $proc.StandardError.ReadToEnd()).Trim()
        $proc.WaitForExit()
    } catch { continue }
    if ($ver -match "25\.") { $JAVAC = $cand; $JAVAC_VER = $ver; break }
}
if (-not $JAVAC) {
    Write-Error "compile-check: JDK 25 javac not found (set JAVA_HOME to a JDK 25)"
    exit 2
}

# --- classpath ----------------------------------------------------------
$OUT = Join-Path $T "tools\.compile-check"
$mcDir = "D:\Gradle\caches\fabric-loom\26.3"
$CP_JARS = @()
foreach ($mc in @("minecraft-common.jar", "minecraft-client.jar", "minecraft-client-only.jar")) {
    $p = Join-Path $mcDir $mc
    if (-not (Test-Path $p)) {
        Write-Error "compile-check: missing Minecraft jar: $p"
        exit 2
    }
    $CP_JARS += ($p -replace "\\", "/")
}
$depJars = Get-ChildItem "D:\Gradle\caches\modules-2\files-2.1" -Recurse -Filter *.jar -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "-(sources|javadoc)\.jar$" } |
    Sort-Object FullName |
    ForEach-Object { $_.FullName -replace "\\", "/" }
if ($depJars.Count -lt 10) {
    Write-Error "compile-check: dependency cache looks empty under D:/Gradle/caches/modules-2"
    exit 2
}
$CP_JARS += $depJars
$CP_BASE = ($CP_JARS -join ";")

# --- pass runner --------------------------------------------------------
function Invoke-Pass([string]$Name, [string]$SrcDir, [string]$Cp, [string]$OutDir) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $srcFile = Join-Path $OUT ("src-" + $Name + ".txt")
    $cpFile = Join-Path $OUT ("cp-" + $Name + ".txt")
    $log = Join-Path $OUT ("javac-" + $Name + ".log")
    $src = @()
    if (Test-Path $SrcDir) {
        $src = Get-ChildItem $SrcDir -Recurse -Filter *.java |
            Sort-Object FullName |
            ForEach-Object { $_.FullName -replace "\\", "/" }
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($srcFile, [string[]]$src, $utf8)
    [IO.File]::WriteAllText($cpFile, ("-cp " + $Cp) + [Environment]::NewLine, $utf8)
    if (Test-Path $log) { Remove-Item $log -Force }
    Write-Output ("[" + $Name + "] sources=" + $src.Count)
    if ($src.Count -gt 0) {
        $env:JAVA_TOOL_OPTIONS = "-Duser.language=en -Duser.country=US"
        $argLine = '--release 25 -proc:none -nowarn -encoding UTF-8 -Xmaxerrs 20000 "@' + $cpFile + '" "@' + $srcFile + '" -d "' + $OutDir + '"'
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $JAVAC
        $psi.Arguments = $argLine
        $psi.WorkingDirectory = $T
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $errTask = $proc.StandardError.ReadToEndAsync()
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $errTask.GetAwaiter().GetResult()
        $proc.WaitForExit()
        [IO.File]::WriteAllText($log, $stdout + $stderr, $utf8)
        Write-Output ("[" + $Name + "] exit=" + $proc.ExitCode)
    } else {
        [IO.File]::WriteAllText($log, "", $utf8)
    }
    $errors = @()
    if (Test-Path $log) { $errors = @(Select-String -LiteralPath $log -Pattern ": error:" -AllMatches) }
    $errCount = $errors.Count
    $classCount = @(Get-ChildItem $OutDir -Recurse -Filter *.class -ErrorAction SilentlyContinue).Count
    Write-Output ("[" + $Name + "] errors=" + $errCount)
    Write-Output ("[" + $Name + "] classes=" + $classCount)
    Write-Output ("[" + $Name + "] log=" + ($log -replace "\\", "/"))
    if ($errCount -gt 0) {
        Write-Output "--- first errors (up to 40) ---"
        $errors | Select-Object -First 40 | ForEach-Object { $_.Line }
    }
    $script:LAST_ERRORS = $errCount
    $script:LAST_CLASSES = $classCount
}

if (Test-Path (Join-Path $OUT "main")) { Remove-Item (Join-Path $OUT "main") -Recurse -Force }
if (Test-Path (Join-Path $OUT "client")) { Remove-Item (Join-Path $OUT "client") -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

Write-Output ("compile-check: javac=" + $JAVAC + " (" + $JAVAC_VER + ")")
Write-Output ("compile-check: classpath jars=" + $CP_JARS.Count + " (MC 3 + modules-2 cache)")

if ($Pass -eq "main" -or $Pass -eq "both") {
    Invoke-Pass "main" (Join-Path $T "src\main\java") $CP_BASE (Join-Path $OUT "main")
    $MAIN_ERRORS = $script:LAST_ERRORS
    $MAIN_CLASSES = $script:LAST_CLASSES
} else { $MAIN_ERRORS = 0; $MAIN_CLASSES = 0 }

if ($Pass -eq "client" -or $Pass -eq "both") {
    $clientCp = ((Join-Path $OUT "main") -replace "\\", "/") + ";" + $CP_BASE
    Invoke-Pass "client" (Join-Path $T "src\client\java") $clientCp (Join-Path $OUT "client")
    $CLIENT_ERRORS = $script:LAST_ERRORS
    $CLIENT_CLASSES = $script:LAST_CLASSES
} else { $CLIENT_ERRORS = 0; $CLIENT_CLASSES = 0 }

Write-Output ("[summary] main errors=" + $MAIN_ERRORS + " classes=" + $MAIN_CLASSES + " | client errors=" + $CLIENT_ERRORS + " classes=" + $CLIENT_CLASSES)
if ($MAIN_ERRORS -eq 0 -and $CLIENT_ERRORS -eq 0) { exit 0 }
exit 1
