$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'build.ps1')
    $applicationClasses = Join-Path $PSScriptRoot 'out/classes'
    $driver = Join-Path $PSScriptRoot 'lib/*'
    & java --enable-native-access=ALL-UNNAMED -cp "$applicationClasses;$driver" Main
    if ($LASTEXITCODE -ne 0) { throw 'Application exited with an error.' }
} finally {
    Pop-Location
}
