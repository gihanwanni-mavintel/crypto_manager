#!/usr/bin/env pwsh

# Frontend Development Server Startup Script

Write-Host ""
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Crypto Position Manager - Dev Server" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# Check if node_modules exists
if (-not (Test-Path "node_modules")) {
    Write-Host "Installing dependencies..." -ForegroundColor Yellow
    Write-Host ""
    npm install --legacy-peer-deps
    Write-Host ""
}

Write-Host "Starting development server..." -ForegroundColor Green
Write-Host ""
Write-Host "Access the app at: http://localhost:3000" -ForegroundColor Cyan
Write-Host ""

npm run dev
