@echo off
echo ========================================
echo   OMNISYNCRA MULTI-PLATFORM DEMO
echo ========================================
echo.
echo This will launch all 4 platforms with context sharing:
echo 📱 Android - Mobile UI
echo 💻 Desktop - Native JVM app  
echo 🌐 Web - JavaScript in browser
echo ⚡ WASM - WebAssembly in browser
echo.
echo When you type in one UI, it will automatically 
echo sync to all other platforms!
echo.
pause

echo [1/4] Starting Desktop (JVM)...
start "Omnisyncra Desktop" cmd /k "echo 💻 Starting Desktop... && ./gradlew :composeApp:run --no-daemon"

echo Waiting 15 seconds for Desktop to initialize...
timeout /t 15 /nobreak

echo [2/4] Starting Web (JavaScript)...  
start "Omnisyncra Web" cmd /k "echo 🌐 Starting Web... && ./gradlew :composeApp:jsBrowserDevelopmentRun --no-daemon"

echo Waiting 15 seconds for Web to initialize...
timeout /t 15 /nobreak

echo [3/4] Starting WASM (WebAssembly)...
start "Omnisyncra WASM" cmd /k "echo ⚡ Starting WASM... && ./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon"

echo Waiting 10 seconds before Android...
timeout /t 10 /nobreak

echo [4/4] Installing Android app...
start "Omnisyncra Android" cmd /k "echo 📱 Installing Android... && ./gradlew :composeApp:installDebug && echo ✅ Android app installed! Launch 'Omnisyncra' from your device."

echo.
echo ========================================
echo   ALL PLATFORMS LAUNCHING!
echo ========================================
echo.
echo Expected URLs:
echo 🌐 Web: http://localhost:8080
echo ⚡ WASM: http://localhost:8081
echo.
echo 🎬 FOR YOUR VIDEO:
echo 1. Show all 4 UIs side by side
echo 2. Type something in one UI
echo 3. Watch it appear in all others!
echo 4. Highlight the platform icons
echo.
echo Press any key to exit launcher...
pause