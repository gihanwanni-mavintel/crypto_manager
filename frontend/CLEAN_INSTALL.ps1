#!/usr/bin/env pwsh

# Clean install script for Windows PowerShell

Write-Host "Cleaning node_modules..." -ForegroundColor Yellow

# Remove node_modules directory
if (Test-Path "node_modules") {
    Remove-Item -Recurse -Force "node_modules" | Out-Null
    Write-Host "✅ Removed node_modules" -ForegroundColor Green
}

# Remove package-lock.json
if (Test-Path "package-lock.json") {
    Remove-Item -Force "package-lock.json" | Out-Null
    Write-Host "✅ Removed package-lock.json" -ForegroundColor Green
}

Write-Host ""
Write-Host "Installing dependencies..." -ForegroundColor Cyan
npm install --legacy-peer-deps

Write-Host ""
Write-Host "✅ Installation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "To start the dev server, run:" -ForegroundColor Cyan
Write-Host "  npm run dev" -ForegroundColor Yellow
