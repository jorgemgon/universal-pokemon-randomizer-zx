# Builds build/ddoz-zx.jar from the UPR-ZX 4.6.1 sources.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root 'build\classes'
$jar = Join-Path $root 'build\ddoz-zx.jar'

if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$list = Join-Path $env:TEMP 'ddoz-zx-sources.txt'
$sources | Set-Content $list
$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
if (-not $javac) { $javac = 'C:\Users\jorge\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\javac.exe' }
$jarTool = Join-Path (Split-Path $javac) 'jar.exe'
if (-not (Test-Path $jarTool)) {
    $javac = 'C:\Users\jorge\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\javac.exe'
    $jarTool = Join-Path (Split-Path $javac) 'jar.exe'
}

& $javac --release 8 -encoding UTF-8 -d $classes "@$list"
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

robocopy (Join-Path $root 'src') $classes /E /XF *.java /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE)" }

& $jarTool cfe $jar com.dabomstew.pkrandom.ddoz.DdozCli -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed ($LASTEXITCODE)" }

Write-Host "built $jar"
