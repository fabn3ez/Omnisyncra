@echo off
echo ========================================
echo Omnisyncra Multi-Platform Quick Demo
echo ========================================
echo.

echo This script will launch all 4 platforms for your video demo.
echo Each platform will open in a separate window.
echo.

echo 1. JVM Desktop (Compose Desktop)
echo 2. Android (if emulator/device connected)  
echo 3. JavaScript Web (Browser)
echo 4. WebAssembly (Browser)
echo.

echo Starting platforms...
echo.

echo [1/4] Starting JVM Desktop...
start "Omnisyncra Desktop" cmd /k "echo Starting Desktop version... && ./gradlew :composeApp:run"

echo Waiting 10 seconds before starting next platform...
timeout /t 10 /nobreak

echo [2/4] Starting JavaScript Web...
start "Omnisyncra Web" cmd /k "echo Starting Web version... && ./gradlew :composeApp:jsBrowserDevelopmentRun"

echo Waiting 10 seconds before starting next platform...
timeout /t 10 /nobreak

echo [3/4] Starting WebAssembly...
start "Omnisyncra WASM" cmd /k "echo Starting WASM version... && ./gradlew :composeApp:wasmJsBrowserDevelopmentRun"

echo Waiting 5 seconds before starting Android...
timeout /t 5 /nobreak

echo [4/4] Starting Android (install to device/emulator)...
start "Omnisyncra Android" cmd /k "echo Installing Android version... && ./gradlew :composeApp:installDebug && echo Android app installed! Check your device/emulator."

echo.
echo ========================================
echo All platforms are starting!
echo ========================================
echo.
echo Desktop: Will open a native window
echo Web: Will open http://localhost:8080 in browser
echo WASM: Will open http://localhost:8081 in browser  
echo Android: Will install to connected device/emulator
echo.
echo Press any key to exit this launcher...
pause