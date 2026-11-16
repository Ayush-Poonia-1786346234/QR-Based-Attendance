# Compatibility launcher: the application now manages its own online tunnel.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run.ps1')
