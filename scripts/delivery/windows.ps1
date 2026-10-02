# Native Windows launcher; Python handles subprocess binary I/O for backup/restore.
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Run this entry in Windows native PowerShell.' }
$python = Get-Command python -ErrorAction Stop
& $python.Source (Join-Path $PSScriptRoot 'manage.py') @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
