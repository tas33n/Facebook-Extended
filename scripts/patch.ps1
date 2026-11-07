param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$Work,
    [Parameter(Mandatory=$true)][string]$Out,
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$Alias,
    [ValidateSet('main','clone','both')][string]$Variant = 'both'
)
$ErrorActionPreference = 'Stop'
& python "$PSScriptRoot\patch.py" $InputPath --variant $Variant --work $Work --out $Out --keystore $Keystore --alias $Alias
if ($LASTEXITCODE -ne 0) { throw "Extended build failed; inspect the working directory logs." }
