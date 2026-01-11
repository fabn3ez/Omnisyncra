@echo off
echo ========================================
echo   OMNISYNCRA DEMO STATUS CHECKER
echo ========================================
echo.

echo Checking platform status...
echo.

echo 💻 Desktop: 
tasklist /FI "WINDOWTITLE eq Omnisyncra Desktop*" 2>nul | find /I "java.exe" >nul
if %ERRORLEVEL%==0 (
    echo    ✅ RUNNING - Desktop app is active
) else (
    echo    ❌ NOT RUNNING - Start with: ./gradlew :composeApp:run
)
echo.

echo 🌐 Web (JavaScript):
netstat -an | find "8080" >nul
if %ERRORLEVEL%==0 (
    echo    ✅ RUNNING - Available at http://localhost:8080
) else (
    echo    ❌ NOT RUNNING - Start with: ./gradlew :composeApp:jsBrowserDevelopmentRun
)
echo.

echo ⚡ WASM (WebAssembly):
netstat -an | find "8081" >nul
if %ERRORLEVEL%==0 (
    echo    ✅ RUNNING - Available at http://localhost:8081
) else (
    echo    ❌ NOT RUNNING - Start with: ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
)
echo.

echo 📱 Android:
adb devices 2>nul | find "device" >nul
if %ERRORLEVEL%==0 (
    echo    ✅ DEVICE CONNECTED - Install with: ./gradlew :composeApp:installDebug
) else (
    echo    ❌ NO DEVICE - Connect Android device and enable USB debugging
)
echo.

echo ========================================
echo   QUICK ACTIONS:
echo ========================================
echo.
echo 1. Run quick-start-demo.bat to start all platforms
echo 2. Open http://localhost:8080 for Web
echo 3. Open http://localhost:8081 for WASM
echo 4. Launch 'Omnisyncra' app on Android device
echo.
pause