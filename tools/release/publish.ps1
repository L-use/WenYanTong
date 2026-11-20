$ErrorActionPreference = 'Stop'
$releasePython = $env:WENYANTONG_PYTHON
if (-not $releasePython) {
    $installedPython = Get-Command python -ErrorAction SilentlyContinue
    if ($installedPython -and $installedPython.Source -notmatch '\\Microsoft\\WindowsApps\\') { $releasePython = $installedPython.Source }
}
if (-not $releasePython) {
    $runtimeGit = Get-Command git -ErrorAction SilentlyContinue
    if ($runtimeGit -and $runtimeGit.Source -match '^(.*[\\/]dependencies)[\\/]native[\\/]git[\\/]cmd[\\/]git.exe$') {
        $bundledPython = Join-Path $Matches[1] 'python/python.exe'
        if (Test-Path -LiteralPath $bundledPython) { $releasePython = $bundledPython }
    }
}
if (-not $releasePython) { throw 'Install Python 3 or set WENYANTONG_PYTHON to its executable path.' }
& $releasePython (Join-Path $PSScriptRoot 'publish.py')
if ($LASTEXITCODE -ne 0) { throw 'Release was not published. Review the error above.' }
