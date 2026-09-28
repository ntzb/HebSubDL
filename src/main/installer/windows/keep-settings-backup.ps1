# Runs inside the new installer, before it removes the previous version.
# jpackage's uninstall wipes the whole install folder, settings included, so
# copy config.properties, db.sqlite and the logs aside; the installer puts
# them back once the new version is in place (see main.wxs).
#
# build.gradle embeds this in main.wxs as a base64 -EncodedCommand, since an
# MSI command line can't safely carry brackets or braces. The installer passes
# the product codes it is about to remove in HEBSUBDL_REMOVING.

$ErrorActionPreference = 'SilentlyContinue'
# progress records reach the MSI log as CLIXML noise
$ProgressPreference = 'SilentlyContinue'
$keep = Join-Path $env:TEMP 'HebSubDL-keep'

# only the install being replaced: on a shared machine the other entries may
# be someone else's, with their credentials
$removing = @($env:HEBSUBDL_REMOVING -split ';' | Where-Object { $_ })
$uninstallKeys = @(
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
    'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
    'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'
)
$dirs = Get-ChildItem -Path $uninstallKeys |
    Where-Object { $removing -contains $_.PSChildName } |
    Get-ItemProperty |
    Where-Object { $_.DisplayName -eq 'HebSubDL' -and $_.InstallLocation } |
    ForEach-Object { $_.InstallLocation }

foreach ($dir in $dirs) {
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'config.properties')))
        { continue }
    # cleared only now: when an earlier install failed after removing the old
    # version, the keep folder is the only copy left of its settings
    Remove-Item -LiteralPath $keep -Recurse -Force
    New-Item -ItemType Directory -Force -Path $keep | Out-Null
    Get-ChildItem -LiteralPath $dir -File |
        Where-Object { $_.Name -eq 'config.properties' -or $_.Name -eq 'db.sqlite' -or
                       ($_.Name -like 'log.log*' -and $_.Name -notlike '*.lck') } |
        Copy-Item -Destination $keep -Force
    break
}
exit 0
