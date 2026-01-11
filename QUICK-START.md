# QUICK START - Get UIs Running NOW

## For Your Video Demo (When You're Tired!)

### The Fastest Way:

1. **Run the quick demo script:**
   ```
   ./quick-demo.bat
   ```

2. **Wait for each platform to start** (they launch with delays)

3. **You'll get 4 UIs:**
   - Desktop window (native app)
   - Web browser tab (JavaScript)  
   - Another browser tab (WebAssembly)
   - Android app (if device connected)

### If Script Doesn't Work:

Open 4 separate command prompts and run:

```bash
# Terminal 1 - Desktop
./gradlew :composeApp:run

# Terminal 2 - Web  
./gradlew :composeApp:jsBrowserDevelopmentRun

# Terminal 3 - WASM
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Terminal 4 - Android
./gradlew :composeApp:installDebug
```

### What You'll See:

- 📱 **Desktop**: Native window with dark theme UI
- 🌐 **Web**: Browser at localhost:8080 
- ⚡ **WASM**: Browser at localhost:8081
- 📱 **Android**: App on your phone/emulator

### For the Video:

1. Show all 4 UIs side by side
2. Point out the platform icons (📱💻🌐⚡)
3. Show they're all running the same app
4. Mention cross-platform Kotlin Multiplatform
5. Done! 

**The compilation errors are fixed - the UIs should work now!**