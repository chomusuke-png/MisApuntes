# Prueba de punta a punta del editor: ejecuta cada herramienta con el editor.js real en Edge sin
# interfaz, pasa el modelo por el núcleo de Word (native/) y revisa el .docx releído.
# Necesita cargo, node y Microsoft Edge. Termina con código 1 si algún caso falla.
$root = $PSScriptRoot
$base = Join-Path $root 'build'
$media = Join-Path $base 'media'
New-Item -ItemType Directory -Force $media | Out-Null

Add-Type -AssemblyName System.Drawing
foreach ($x in @(@('test.png',200,100,'Red'),@('rot.png',100,200,'Blue'),@('other.png',60,60,'Green'))) {
  $b = New-Object System.Drawing.Bitmap $x[1],$x[2]
  $g = [System.Drawing.Graphics]::FromImage($b); $g.Clear([System.Drawing.Color]::FromName($x[3])); $g.Dispose()
  $b.Save((Join-Path $media $x[0]), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
}

$manifest = Join-Path $root 'verify\Cargo.toml'
Copy-Item (Join-Path $root '..\..\native\Cargo.lock') (Join-Path $root 'verify\Cargo.lock') -Force
$env:CARGO_TARGET_DIR = Join-Path $base 'target'
cargo build --release --manifest-path $manifest 2>&1 | Select-Object -Last 5
$verify = Join-Path $base 'target\release\verify.exe'
if (-not (Test-Path $verify)) { 'No compiló el verificador'; exit 1 }

& $verify fixture $base
node (Join-Path $root 'gen.js') $base

$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$url = 'file:///' + ((Join-Path $base 'harness.html') -replace '\\','/')
$profileDir = Join-Path $base ('edge-' + [guid]::NewGuid().ToString('N'))
$dom = Join-Path $base 'dom.html'
$edgeArgs = @('--headless=new','--disable-gpu','--no-first-run','--allow-file-access-from-files',"--user-data-dir=$profileDir",'--virtual-time-budget=400000','--window-size=1000,1200','--dump-dom',$url)
$p = Start-Process -FilePath $edge -ArgumentList $edgeArgs -RedirectStandardOutput $dom -PassThru -WindowStyle Hidden
if (-not $p.WaitForExit(280000)) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue; 'Edge no terminó a tiempo'; exit 1 }
Remove-Item $profileDir -Recurse -Force -ErrorAction SilentlyContinue

$m = [regex]::Match((Get-Content $dom -Raw -Encoding UTF8), '<pre id="results">([^<]*)</pre>')
if (-not $m.Success -or -not $m.Groups[1].Value) { 'Edge no entregó resultados'; exit 1 }
$json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($m.Groups[1].Value))
[IO.File]::WriteAllText((Join-Path $base 'cases.json'), $json, (New-Object Text.UTF8Encoding $false))

& $verify check $base
exit $LASTEXITCODE
