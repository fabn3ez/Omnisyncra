@echo off
echo Starting Omnisyncra Multi-Platform Demo
echo.

echo 1. Starting JVM Desktop version...
start "JVM Desktop" cmd /c "gradlew :composeApp:run"

timeout /t 5

echo 2. Starting Android (if emulator is running)...
start "Android" cmd /c "gradlew :composeApp:installDebug"

timeout /t 5

echo 3. Starting JavaScript Web version...
start "JavaScript Web" cmd /c "gradlew :composeApp:jsBrowserDevelopmentRun --continuous"

timeout /t 5

echo 4. Starting WebAssembly version...
start "WebAssembly" cmd /c "gradlew :composeApp:wasmJsBrowserDevelopmentRun --continuous"

echo.
echo All platforms started! Check the opened windows.
echo Press any key to exit...
pause