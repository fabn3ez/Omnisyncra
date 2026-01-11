@echo off
echo ========================================
echo   OMNISYNCRA QUICK DEMO LAUNCHER
echo ========================================
echo.
echo 🚀 Starting all 4 platforms for video demo...
echo.

echo [1/4] 💻 Desktop is already running!
echo Check your desktop for the Omnisyncra window.
echo.

echo [2/4] 🌐 Starting Web (JavaScript)...
start "Omnisyncra Web" cmd /k "echo 🌐 Web starting... && ./gradlew :composeApp:jsBrowserDevelopmentRun --no-daemon && echo ✅ Web ready at http://localhost:8080"
echo.

echo [3/4] ⚡ Starting WASM (WebAssembly)...
start "Omnisyncra WASM" cmd /k "echo ⚡ WASM starting... && ./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon && echo ✅ WASM ready at http://localhost:8081"
echo.

echo [4/4] 📱 Installing Android app...
start "Omnisyncra Android" cmd /k "echo 📱 Installing Android... && ./gradlew :composeApp:installDebug && echo ✅ Android installed! Launch 'Omnisyncra' from your device."
echo.

echo ========================================
echo   🎬 FOR YOUR VIDEO DEMO:
echo ========================================
echo.
echo 1. Wait for all platforms to load (2-3 minutes)
echo 2. Open these URLs in your browser:
echo    🌐 Web: http://localhost:8080
echo    ⚡ WASM: http://localhost:8081
echo.
echo 3. Show all 4 UIs side by side:
echo    💻 Desktop window
echo    🌐 Web browser tab
echo    ⚡ WASM browser tab  
echo    📱 Android device
echo.
echo 4. Type something in ANY input field
echo 5. Watch it sync to ALL other platforms!
echo 6. Highlight the platform icons and real-time sync
echo.
echo Press any key to exit launcher...
pause