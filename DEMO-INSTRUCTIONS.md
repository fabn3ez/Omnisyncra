# Omnisyncra Multi-Platform Demo Instructions

## Quick Start for Video Demo

### Option 1: Use the Quick Demo Script
```bash
./quick-demo.bat
```

This will automatically launch all 4 platforms in sequence with proper timing.

### Option 2: Manual Launch (if script doesn't work)

Launch each platform manually in separate terminals:

#### 1. JVM Desktop
```bash
./gradlew :composeApp:run
```
- Opens a native desktop window
- Shows the full Omnisyncra UI with device discovery

#### 2. JavaScript Web  
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```
- Opens browser at http://localhost:8080
- Web version of the UI

#### 3. WebAssembly
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  
```
- Opens browser at http://localhost:8081
- WASM version for better performance

#### 4. Android
```bash
./gradlew :composeApp:installDebug
```
- Installs app to connected Android device/emulator
- Launch the "Omnisyncra" app from the device

## What to Show in Your Video

1. **Desktop App**: Native window with full UI
2. **Web Browser**: JavaScript version running in Chrome/Firefox
3. **WASM Browser**: WebAssembly version (faster, more responsive)
4. **Android Device**: Mobile app on phone/emulator

## Expected UI Features

Each platform shows:
- 📱 Device discovery with platform icons
- 🔗 Cross-platform connectivity 
- 💬 Real-time messaging
- 📊 System monitoring
- 🎨 Premium dark theme UI
- ⚡ Platform-specific optimizations

## Troubleshooting

If builds are slow:
- Use `--no-daemon` flag: `./gradlew :composeApp:run --no-daemon`
- Close other applications to free up memory
- Use the quick-demo.bat script which handles timing

If Android doesn't work:
- Make sure USB debugging is enabled
- Check `adb devices` shows your device
- Try using an Android emulator instead

## Demo Tips

- Start with Desktop (fastest to launch)
- Show the UI responding on each platform
- Highlight the platform icons (📱💻🌐⚡)
- Demonstrate the cross-platform nature
- Keep it short and focused on the multi-platform aspect