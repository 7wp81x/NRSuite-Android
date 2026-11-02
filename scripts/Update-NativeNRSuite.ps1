# Run from any directory. All arguments are forwarded literally to Python.
$ErrorActionPreference = 'Stop'
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCommand) {
    throw 'Python 3.10 or newer is required. Install Python, then rerun this script.'
}
& $pythonCommand.Source "$PSScriptRoot\update_native_nrsuite.py" @args
exit $LASTEXITCODE
