# Omnisyncra - Multiplatform Synchronization System

Omnisyncra is a Kotlin Multiplatform project that enables seamless device discovery, synchronization, and collaboration across multiple platforms. It provides real-time device monitoring, AI-powered context sharing, and cross-platform communication capabilities.

## 🚀 Supported Platforms

- **Android** - Native Android application
- **Desktop (JVM)** - Cross-platform desktop application (Windows, macOS, Linux)
- **Web (JavaScript)** - Browser-based application
- **Web (WASM)** - WebAssembly-based application (experimental)

## 📋 Prerequisites

Before running Omnisyncra, ensure you have the following installed:

### Required Software

1. **Java Development Kit (JDK) 11 or higher**
   - Download from [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
   - Verify installation: `java -version`

2. **Android Studio** (for Android development)
   - Download from [Android Studio](https://developer.android.com/studio)
   - Install Android SDK (API level 24 or higher)
   - Set up Android emulator or connect physical device

3. **Git** (for version control)
   - Download from [Git](https://git-scm.com/)
   - Verify installation: `git --version`

### Optional Software

- **IntelliJ IDEA** (recommended IDE for Kotlin development)
- **Node.js** (automatically managed by Gradle for web targets)

## 🛠️ Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/fabn3ez/Omnisyncra.git
cd Omnisyncra
```

### 2. Configure API Keys (Optional)

Create a `local.properties` file in the root directory:

```properties
# Optional: Add your Gemini API key for AI features
gemini.api.key=your_api_key_here
```

### 3. Verify Setup

Check that Gradle can build the project:

```bash
# On Windows
.\gradlew.bat build

# On macOS/Linux
./gradlew build
```

## 🏃‍♂️ Running the Applications

### Android Application

#### Option 1: Using Android Studio
1. Open the project in Android Studio
2. Select "composeApp" configuration
3. Choose your target device (emulator or physical device)
4. Click the "Run" button

#### Option 2: Command Line
```bash
# Build the debug APK
.\gradlew.bat :composeApp:assembleDebug

# Install on connected device
.\gradlew.bat :composeApp:installDebug
```

**APK Location**: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### Desktop (JVM) Application

#### Quick Start
```bash
# Run directly
.\gradlew.bat :composeApp:run
```

#### Build Distributable
```bash
# Create native installer
.\gradlew.bat :composeApp:createDistributable

# Package as installer (Windows MSI, macOS DMG, Linux DEB)
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

**Output Location**: `composeApp/build/compose/binaries/main/`

### Web Application (JavaScript)

#### Development Server
```bash
# Start development server
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

- **URL**: http://localhost:8080
- **Hot Reload**: Enabled in development mode
- **Browser Compatibility**: Modern browsers (Chrome, Firefox, Safari, Edge)

#### Production Build
```bash
# Build for production
.\gradlew.bat :composeApp:jsBrowserProductionWebpack
```

**Output Location**: `composeApp/build/dist/js/productionExecutable/`

### Web Application (WASM) - Experimental

> **Note**: WASM target is experimental and may have limited functionality

```bash
# Development server
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun

# Production build
.\gradlew.bat :composeApp:wasmJsBrowserProductionWebpack
```

- **URL**: http://localhost:8080
- **Requirements**: Modern browser with WASM support
- **Performance**: Faster than JS version, smaller bundle size

## 🔧 Development Commands

### Building Specific Targets

```bash
# Android
.\gradlew.bat :composeApp:assembleDebug

# Desktop
.\gradlew.bat :composeApp:jar

# JavaScript
.\gradlew.bat :composeApp:compileKotlinJs

# WASM
.\gradlew.bat :composeApp:compileKotlinWasmJs
```

### Testing

```bash
# Run all tests
.\gradlew.bat test

# Run specific platform tests
.\gradlew.bat :composeApp:testDebugUnitTest  # Android
.\gradlew.bat :composeApp:jvmTest            # JVM
.\gradlew.bat :composeApp:jsTest             # JavaScript
```

### Cleaning

```bash
# Clean all build artifacts
.\gradlew.bat clean

# Clean specific target
.\gradlew.bat :composeApp:clean
```

## 📱 Platform-Specific Features

### Android
- **Device Discovery**: Network scanning and Bluetooth discovery
- **Background Services**: Continuous synchronization
- **Native Notifications**: Real-time updates
- **Hardware Integration**: Camera, sensors, GPS

### Desktop (JVM)
- **System Monitoring**: CPU, memory, network usage
- **File System Access**: Local storage and file sharing
- **Multi-window Support**: Advanced UI capabilities
- **Native Integrations**: OS-specific features

### Web (JS/WASM)
- **Browser APIs**: WebRTC, WebSockets, Local Storage
- **Cross-platform Compatibility**: Works on any modern browser
- **Progressive Web App**: Installable web application
- **Real-time Communication**: WebSocket-based synchronization

## 🌐 Network Configuration

### Default Ports
- **WebSocket Server**: 8080-8090 (auto-discovery)
- **HTTP Server**: 8080 (web development)
- **Discovery Protocol**: UDP broadcast on local network

### Firewall Configuration
Ensure the following ports are open for full functionality:
- **TCP 8080-8090**: WebSocket communication
- **UDP 8080-8090**: Device discovery
- **TCP 3000**: Development server (if different)

## 🚨 Troubleshooting

### Common Issues

#### Android Build Fails
```bash
# Update Android SDK
# In Android Studio: Tools > SDK Manager > Update

# Clean and rebuild
.\gradlew.bat clean
.\gradlew.bat :composeApp:assembleDebug
```

#### Desktop App Won't Start
```bash
# Check Java version
java -version

# Verify JAVA_HOME is set
echo $JAVA_HOME  # macOS/Linux
echo %JAVA_HOME% # Windows
```

#### Web App Build Errors
```bash
# Clear Node.js cache
.\gradlew.bat :kotlinNpmInstall --rerun-tasks

# Clean and rebuild
.\gradlew.bat clean
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

#### Network Discovery Issues
- Check firewall settings
- Ensure devices are on the same network
- Verify port availability: `netstat -an | findstr 8080`

### Performance Optimization

#### Android
- Enable R8 code shrinking in release builds
- Use ProGuard for additional optimization
- Test on physical devices for accurate performance

#### Desktop
- Increase JVM heap size: `-Xmx4g`
- Enable JIT compilation optimizations
- Use native packaging for better startup time

#### Web
- Enable production mode for smaller bundles
- Use WASM target for better performance
- Implement code splitting for large applications

## 📚 Project Structure

```
Omnisyncra/
├── composeApp/src/
│   ├── commonMain/kotlin/     # Shared code
│   ├── androidMain/kotlin/    # Android-specific code
│   ├── jvmMain/kotlin/        # Desktop-specific code
│   ├── jsMain/kotlin/         # JavaScript-specific code
│   └── wasmJsMain/kotlin/     # WASM-specific code
├── gradle/                    # Gradle wrapper and dependencies
├── build.gradle.kts          # Root build configuration
└── local.properties          # Local configuration (API keys, etc.)
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the Apache License - see the [LICENSE](LICENSE) file for details.


**Status**: 
- ✅ Android - Fully functional
- ✅ Desktop (JVM) - Fully functional  
- ✅ Web (JavaScript) - Functional with some limitations
- ⚠️ Web (WASM) - Experimental, limited functionality

For support or questions, please open an issue in the repository.