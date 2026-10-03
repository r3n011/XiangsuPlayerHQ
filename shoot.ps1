param(
  [string]$Query = "",
  [string]$Out = "frame.png",
  [int]$Width = 1000,
  [int]$Height = 650,
  [int]$Budget = 9000
)

# Headless Edge + SwiftShader screenshot for the WebGL page.
# Fallback for when the browser tool is unavailable: software rasterization,
# slow but it produces real frames.
$ms = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
$shotDir = Join-Path $env:TEMP "opencode\shots"
$outPath = Join-Path $shotDir $Out
New-Item -ItemType Directory -Force -Path $shotDir | Out-Null
if (Test-Path $outPath) { Remove-Item $outPath -Force }

$url = "http://localhost:8765/game/rain-eave.html"
if ($Query) { $url = $url + "?" + $Query }

# Note: must not be named $args, that is a PowerShell automatic variable.
$edgeArgs = @(
  "--headless=new",
  "--disable-gpu",
  "--use-gl=swiftshader",
  "--enable-unsafe-swiftshader",
  "--hide-scrollbars",
  "--force-device-scale-factor=1",
  "--run-all-compositor-stages-before-draw",
  "--screenshot=$outPath",
  "--window-size=$Width,$Height",
  "--virtual-time-budget=$Budget",
  "--user-data-dir=$env:TEMP\opencode\edgeprofile",
  $url
)

& $ms @edgeArgs 2>&1 | Out-Null

if (Test-Path $outPath) {
  "OK  $Out  $([math]::Round((Get-Item $outPath).Length/1KB,1)) KB"
} else {
  "FAILED: no screenshot for $url"
  exit 1
}