@echo off
REM Frontend Development Server Startup Script

echo.
echo ====================================
echo Crypto Position Manager - Dev Server
echo ====================================
echo.

REM Check if node_modules exists
if not exist "node_modules" (
    echo Installing dependencies...
    echo.
    call npm install --legacy-peer-deps
    echo.
)

echo Starting development server...
echo.
echo Access the app at: http://localhost:3000
echo.

call npm run dev
