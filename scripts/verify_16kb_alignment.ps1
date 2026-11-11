param (
    [string]$SoDir = "app/build/intermediates/stripped_native_libs/release/out/lib/arm64-v8a"
)

Write-Host ">>> Scanning native shared libraries in $SoDir for 16KB page alignment..." -ForegroundColor Cyan

if (-not (Test-Path $SoDir)) {
    Write-Host "Directory $SoDir does not exist yet. Please compile native targets first." -ForegroundColor Yellow
    exit 0
}

$soFiles = Get-ChildItem -Path $SoDir -Filter "*.so"
if ($soFiles.Count -eq 0) {
    Write-Host "No .so files found in $SoDir." -ForegroundColor Yellow
    exit 0
}

Write-Host "Found $($soFiles.Count) libraries to verify."
