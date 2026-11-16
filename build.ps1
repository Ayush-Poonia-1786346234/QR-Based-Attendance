$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $driver = Join-Path $PSScriptRoot 'lib/mysql-connector-j-26.10.0.jar'
    if (-not (Test-Path -LiteralPath $driver)) { throw "Missing JDBC driver: $driver" }
    foreach ($library in @('zxing-core-3.5.4.jar', 'webcam-capture-0.3.12.jar', 'bridj-0.7.0.jar', 'slf4j-api-1.7.36.jar', 'slf4j-simple-1.7.36.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot "lib/$library"))) { throw "Missing library: lib/$library" }
    }
    $outputDirectory = Join-Path $PSScriptRoot 'out/classes'
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    $sources = @(Get-ChildItem -LiteralPath 'src/main/java' -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac --release 17 -encoding UTF-8 -cp (Join-Path $PSScriptRoot 'lib/*') -d $outputDirectory @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Copy-Item -Path 'src/main/resources/*' -Destination $outputDirectory -Recurse -Force
    Write-Host 'Build successful. Run .\run.ps1 to start the application.'
} finally {
    Pop-Location
}
