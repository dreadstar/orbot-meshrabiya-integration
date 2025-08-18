# Orbot Meshrabiya Integration

A privacy-preserving mesh networking integration for Orbot, enabling secure, decentralized communication and content sharing in challenging network environments.

## Features

- Multi-hop mesh networking with dynamic role assignment
- End-to-end encrypted communications
- Tor network integration
- Privacy-preserving logging
- Intuitive UI with app selection
- Real-time network monitoring

## 🏗️ Project Architecture

This project integrates three main components to create a seamless privacy-preserving mesh networking solution:

### 📱 Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Orbot-Meshrabiya Integration             │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   orbot-android │  │   integration   │  │ lib-meshrabiya│ │
│  │                 │  │                 │  │              │ │
│  │  • Tor Proxy    │◄─┤  • UI Bridge    ├─►│ • Mesh Core  │ │
│  │  • VPN Service  │  │  • State Mgmt   │  │ • P2P Network│ │
│  │  • Network Mgmt │  │  • Capabilities │  │ • Discovery  │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 🔌 orbot-android (Submodule)

The **orbot-android** component provides the foundational privacy and networking infrastructure:

**Core Responsibilities:**
- **Tor Network Integration**: Manages Tor circuit creation, SOCKS proxy configuration, and onion routing
- **VPN Service Management**: Handles Android VPN service lifecycle and traffic routing
- **Network State Monitoring**: Tracks connectivity changes and network interface status
- **SOCKS Proxy Server**: Provides local SOCKS proxy endpoints for mesh traffic routing

**Key Components:**
- `OrbotService`: Main service managing Tor daemon and VPN operations
- `TorService`: Direct interface to the Tor network daemon
- `VpnService`: Android VPN service implementation for traffic interception
- `ProxyManager`: Manages SOCKS proxy configurations and endpoints

### 🔗 integration (Main Module)

The **integration** module serves as the orchestration layer between Orbot and Meshrabiya:

**Core Responsibilities:**
- **Bidirectional Communication Bridge**: Facilitates seamless data exchange between Orbot and Meshrabiya
- **UI Integration**: Provides unified user interface components and state management
- **Capability Management**: Coordinates mesh networking capabilities with Tor routing
- **Service Coordination**: Manages lifecycle and dependencies between components

**Key Components:**
- `MeshIntegrationService`: Primary service coordinating mesh and Tor operations
- `GatewayCapabilitiesManager`: Manages mesh gateway roles and Tor exit capabilities
- `MeshFragment`: UI component for mesh network visualization and control
- `MeshViewModel`: State management for mesh network UI and data binding

### 🕸️ lib-meshrabiya (Submodule)

The **lib-meshrabiya** component provides the core mesh networking functionality:

**Core Responsibilities:**
- **Mesh Network Formation**: Handles device discovery, neighbor detection, and topology management
- **Multi-hop Routing**: Implements intelligent packet routing across mesh nodes
- **P2P Communication**: Manages direct peer-to-peer connections via WiFi Direct and Bluetooth
- **Network Resilience**: Provides self-healing network capabilities and dynamic role assignment

**Key Components:**
- `AndroidVirtualNode`: Main mesh node implementation for Android devices
- `MeshrabiyaConnectLink`: Manages connection establishment and maintenance
- `VirtualRouter`: Handles packet forwarding and route optimization
- `EmergentRoleManager`: Dynamically assigns mesh roles based on device capabilities

### 🔄 Bidirectional Communication Architecture

The integration achieves seamless bidirectional communication through several key mechanisms:

#### 1. **Service-Level Integration**
```kotlin
// MeshIntegrationService coordinates both services
class MeshIntegrationService : Service() {
    private val orbotManager = OrbotServiceManager()
    private val meshManager = MeshrabiyaServiceManager()
    
    override fun onCreate() {
        // Initialize bidirectional service communication
        orbotManager.setMeshCallback(meshManager::onTorStatusChange)
        meshManager.setTorCallback(orbotManager::onMeshStatusChange)
    }
}
```

#### 2. **SOCKS Proxy Integration**
- **Orbot → Mesh**: Mesh traffic is routed through Orbot's SOCKS proxy for Tor anonymization
- **Mesh → Orbot**: Mesh nodes can serve as Tor relay points using dynamic proxy configuration
- **Traffic Flow**: `Mesh Node → SOCKS Proxy → Tor Network → Destination`

#### 3. **Capability Synchronization**
```kotlin
// GatewayCapabilitiesManager synchronizes capabilities
class GatewayCapabilitiesManager {
    fun updateMeshCapabilities(torStatus: TorStatus) {
        val canServeTor = torStatus.isConnected && torStatus.hasRelay
        meshNode.updateGatewayRole(if (canServeTor) GATEWAY else MESH_ONLY)
    }
    
    fun updateTorCapabilities(meshStatus: MeshStatus) {
        val hasInternet = meshStatus.hasGatewayNodes
        torManager.setExitPolicy(if (hasInternet) ALLOW_EXIT else RESTRICT_EXIT)
    }
}
```

#### 4. **Shared State Management**
- **Network State**: Both components share network connectivity status and routing tables
- **Device Capabilities**: Battery, bandwidth, and processing power metrics are shared for optimal role assignment
- **Security Context**: Encryption keys and trust relationships are coordinated across both networks

### 🎨 UI Integration Architecture

The user interface integration provides a unified experience across both networking layers:

#### 1. **MeshFragment Integration**
```kotlin
class MeshFragment : Fragment() {
    private val meshViewModel: MeshViewModel by viewModels()
    private val orbotViewModel: OrbotViewModel by activityViewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Observe both mesh and Tor status
        meshViewModel.networkStatus.observe(this) { updateMeshUI(it) }
        orbotViewModel.torStatus.observe(this) { updateTorUI(it) }
        
        // Combined status display
        combineLatest(meshViewModel.networkStatus, orbotViewModel.torStatus)
            .observe(this) { (mesh, tor) -> updateCombinedStatus(mesh, tor) }
    }
}
```

#### 2. **Real-time Status Coordination**
- **Network Visualization**: Live mesh topology overlaid with Tor circuit information
- **Traffic Monitoring**: Combined bandwidth usage from both mesh and Tor networks
- **Status Indicators**: Unified connection status showing both mesh connectivity and Tor anonymization

#### 3. **User Controls**
- **Integrated Settings**: Single configuration interface for both mesh and Tor preferences
- **App Selection**: Choose which apps route through mesh-only, Tor-only, or combined mesh+Tor
- **Privacy Controls**: Granular control over data sharing between mesh and Tor networks

### 📊 Data Flow Architecture

```
┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   App Data  │───►│  Integration    │───►│  lib-meshrabiya │
└─────────────┘    │     Module      │    │   (Mesh Route)  │
                   │                 │    └─────────────────┘
┌─────────────┐    │  • Route Logic  │    ┌─────────────────┐
│ User Config │───►│  • State Sync   │───►│  orbot-android  │
└─────────────┘    │  • UI Bridge    │    │  (Tor Route)    │
                   └─────────────────┘    └─────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │  Combined Output│
                   │ (Mesh + Tor)    │
                   └─────────────────┘
```

This architecture ensures that users benefit from both the anonymity of Tor and the resilience of mesh networking, with seamless switching between modes based on network conditions and user preferences.

## Building

1. Clone and initialize submodules:
   ```bash
   git clone https://github.com/yourusername/orbot-meshrabiya-integration.git
   cd orbot-meshrabiya-integration
   git submodule update --init --recursive
   ```

2. Open in Android Studio:
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to and select the project directory
   - Wait for the project to load and index

3. Sync Gradle files:
   - Ensure Gradle Plugin installed
   - Click the "Gradle Elephant" on the toolbar
   - Select the download sources icon
   - Select "Sync Project with Gradle Files" (or press Ctrl+Shift+O / Cmd+Shift+O)
   - Wait for the sync to complete

4. Build the project:
   - For debug build:
     ```bash
     ./gradlew :integration:assembleDebug
     ```
     The debug APK will be generated at: `integration/build/outputs/apk/debug/integration-debug.apk`
   
   - For release build:
     ```bash
     ./gradlew :integration:assembleRelease
     ```
     The release APK will be generated at: `integration/build/outputs/apk/release/integration-release.apk`

5. Run the app:
   - Select your target device from the device dropdown
   - Click the "Run" button (green play icon) or press Shift+F10 / Control+R

## Testing

Run tests from Android Studio:
- Unit tests: `orbot-android/app/src/test`
- Integration tests: `orbot-android/app/src/androidTest`
- UI tests: `orbot-android/app/src/androidTest/java/org/torproject/android/ui`

Run ALL tests with Coverage analysis from terminal
- ,/gradlew runAllTests --console=plain
- ./gradlew aggregatedCoverageReport --console=plain


## License

GPL v3

## 🛠️ Building the Project

### Prerequisites
- Android Studio Arctic Fox (2020.3.1) or newer
- JDK 11 or newer
- Android SDK 31 or newer
- Git

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/orbot-meshrabiya-integration.git
   cd orbot-meshrabiya-integration
   ```

2. Initialize submodules:
   ```bash
   git submodule update --init --recursive
   ```

3. Open the project in Android Studio:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory and select it

### Building
1. Sync Gradle files:
   - Click "Sync Project with Gradle Files" in the toolbar
   - Wait for the sync to complete

2. Build the project:
   - Select "Build > Make Project" from the menu
   - Or use the keyboard shortcut (Ctrl+F9 on Windows/Linux, Cmd+F9 on macOS)

## 🧪 Testing

### Unit Tests
Run unit tests from Android Studio:
1. Open the Project view
2. Navigate to `orbot-android/app/src/test`
3. Right-click on the test directory
4. Select "Run Tests"

### Integration Tests
Run integration tests:
1. Open the Project view
2. Navigate to `orbot-android/app/src/androidTest`
3. Right-click on the test directory
4. Select "Run Tests"

### UI Tests
Run UI tests:
1. Open the Project view
2. Navigate to `orbot-android/app/src/androidTest/java/org/torproject/android/ui`
3. Right-click on the test directory
4. Select "Run Tests"

## 📱 Running the App

1. Connect an Android device or start an emulator
2. Click the "Run" button (green triangle) in the toolbar
3. Select your device/emulator
4. Wait for the app to install and launch

## 🔍 Debugging

### Logging
- Use Android Studio's Logcat to view logs
- Filter by tag:
  - `MeshrabiyaService` for service logs
  - `MeshViewModel` for UI state logs
  - `MeshFragment` for UI interaction logs

### Beta Testing
1. Enable beta testing in the app settings
2. Choose logging level:
   - Basic: Essential operational logs
   - Detailed: Extended diagnostic information
   - Full: Complete system state logging

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## 🙏 Acknowledgments

- Orbot Project for the base VPN functionality
- Meshrabiya Project for the mesh networking implementation
- All contributors and testers




# 📦 Project Setup & Development Guidelines

This document outlines the setup requirements, version compatibility, and common commands for working on this project.

---

## ✅ Requirements

Ensure your environment uses **consistent versions** across all modules to avoid compatibility issues:

| Component                 | Version                      |
|--------------------------|------------------------------|
| **Android Gradle Plugin (AGP)** | `8.2.0`              |
| **Kotlin**               | `2.0.0`                      |
| **Kotlin Serialization** | `2.0.0`                      |
| **Gradle**               | `8.4` *(recommended)*        |
| **Java**                 | `17`                         |
| **Android Studio**       | `Narwhal | 2025.1.1 Patch 1` |

> ⚠️ **Always use the same AGP and Kotlin versions across all modules** for stable builds and IDE support.

---

## 🧪 Running Unit Tests

### 🔹 Step-by-step

1. Open a terminal in the **project root directory**
2. Run the following command:

```bash
./gradlew runAllTests
```

---

## 👨‍💻 Developer Onboarding Guide

This comprehensive guide will help new developers get set up with the Orbot Meshrabiya Integration project in both VS Code and Android Studio environments.

### 📋 Prerequisites

Before starting, ensure you have the following installed:

- **Git** (2.30+): For version control and submodule management
- **JDK 17**: Required for Android development and Gradle builds
- **Android SDK**: Platform tools and build tools (installed via IDE or command line)
- **VS Code** OR **Android Studio**: Choose your preferred development environment

### 🔄 Repository Setup

#### 1. Clone the Repository with Submodules

The project uses Git submodules for the Orbot and Meshrabiya components. Follow these steps:

```bash
# Clone the main repository
git clone --recurse-submodules https://github.com/dreadstar/orbot-meshrabiya-integration.git

# If you already cloned without submodules, initialize them:
cd orbot-meshrabiya-integration
git submodule update --init --recursive

# Verify submodules are properly initialized
git submodule status
```

#### 2. Switch to Development Branch (if applicable)

```bash
# Check current branch
git branch -a

# Switch to development branch if needed
git checkout version-update

# Update submodules for the current branch
git submodule update --remote --recursive
```

#### 3. Keep Submodules Updated

```bash
# Update all submodules to latest commits
git submodule update --remote --recursive

# Update specific submodule
git submodule update --remote orbot-android
git submodule update --remote Meshrabiya
```

### 🆚 VS Code Setup

#### Required Extensions

Install these essential extensions for Android development:

```bash
# Install via command palette (Ctrl+Shift+P / Cmd+Shift+P)
# Type: "Extensions: Install Extensions" and search for:

# Core Extensions
- Extension Pack for Java (Microsoft)
- Kotlin Language (mathiasfrohlich.Kotlin)
- Android iOS Emulator (DiemasMichiels.emulate)
- Gradle for Java (vscjava.vscode-gradle)

# Optional but Recommended
- GitLens — Git supercharged (eamodio.gitlens)
- Bracket Pair Colorizer 2 (CoenraadS.bracket-pair-colorizer-2)
- Error Lens (usernamehw.errorlens)
- Material Icon Theme (PKief.material-icon-theme)
```

#### VS Code Settings Configuration

Create or update `.vscode/settings.json`:

```json
{
    "java.home": "/path/to/your/jdk17",
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-17",
            "path": "/path/to/your/jdk17"
        }
    ],
    "java.compile.nullAnalysis.mode": "automatic",
    "java.gradle.buildServer.enabled": "on",
    "kotlin.languageServer.enabled": true,
    "files.exclude": {
        "**/build/": true,
        "**/.gradle/": true,
        "**/local.properties": true
    },
    "search.exclude": {
        "**/build": true,
        "**/node_modules": true,
        "**/.gradle": true
    },
    "java.test.config": {
        "workingDirectory": "${workspaceFolder}"
    },
    "gradle.nestedProjects": true
}
```

#### VS Code Tasks Configuration

Create `.vscode/tasks.json` for common Gradle tasks:

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Build Project",
            "type": "shell",
            "command": "./gradlew",
            "args": ["build"],
            "group": "build",
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "shared"
            }
        },
        {
            "label": "Run All Tests",
            "type": "shell",
            "command": "./gradlew",
            "args": ["runAllTests"],
            "group": "test",
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "shared"
            }
        },
        {
            "label": "Clean Build",
            "type": "shell",
            "command": "./gradlew",
            "args": ["clean", "build"],
            "group": "build"
        }
    ]
}
```

### 🤖 Android Studio Setup

#### Recommended Version
- **Android Studio Narwhal | 2025.1.1 Patch 1** or newer
- Ensure you have the latest Android SDK and build tools

#### Initial Project Import

1. **Open Android Studio**
2. **Select "Open an existing project"**
3. **Navigate to the cloned repository directory**
4. **Select the root `orbot-meshrabiya-integration` folder**
5. **Click "OK" and wait for project indexing**

#### Android Studio Settings Recommendations

##### 1. Gradle Settings
- **File → Settings → Build → Gradle**
  - Build and run using: `Gradle`
  - Run tests using: `Gradle`
  - Gradle JVM: `Use JAVA_HOME` or select JDK 17

##### 2. Code Style Settings
- **File → Settings → Editor → Code Style → Kotlin**
  - Set from: `Kotlin style guide`
  - **File → Settings → Editor → Code Style → Java**
  - Set from: `Android`

##### 3. Memory Settings (for large projects)
- **Help → Edit Custom VM Options**
  ```
  -Xms2048m
  -Xmx4096m
  -XX:ReservedCodeCacheSize=512m
  -XX:+UseConcMarkSweepGC
  -XX:SoftRefLRUPolicyMSPerMB=50
  -ea
  -XX:CICompilerCount=2
  -Dsun.io.useCanonPrefixCache=false
  -Djdk.http.auth.tunneling.disabledSchemes=""
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:-OmitStackTraceInFastThrow
  -Djb.vmOptionsFile=${idea.paths.selector}/studio.vmoptions
  ```

##### 4. Build Settings
- **File → Settings → Build → Compiler**
  - Command-line Options: `--stacktrace --info`
  - Build process heap size: `4096 MB`

### 🔧 Common Development Commands

#### Building the Project

```bash
# Clean build (removes all build artifacts)
./gradlew clean

# Build all modules
./gradlew build

# Build specific module
./gradlew :integration:build
./gradlew :Meshrabiya:lib-meshrabiya:build

# Assemble debug APK
./gradlew :integration:assembleDebug

# Assemble release APK
./gradlew :integration:assembleRelease
```

#### Testing Commands

```bash
# Run all tests with coverage
./gradlew runAllTests

# Run only unit tests
./gradlew test

# Run integration tests
./gradlew :integration:test

# Run specific test class
./gradlew :integration:test --tests="com.example.MeshIntegrationTest"

# Generate coverage reports
./gradlew jacocoTestReport
```

#### Debugging and Diagnostics

```bash
# Check project dependencies
./gradlew dependencies

# Analyze build performance
./gradlew build --profile

# Check for dependency updates
./gradlew dependencyUpdates

# Validate Gradle wrapper
./gradlew wrapper --gradle-version=8.4
```

### 🐛 Troubleshooting Common Issues

#### Submodule Issues
```bash
# Reset submodules if they're in a bad state
git submodule deinit --all -f
git submodule update --init --recursive

# Force update submodules
git submodule foreach --recursive git reset --hard
git submodule update --remote --recursive
```

#### Gradle Issues
```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches/

# Refresh dependencies
./gradlew build --refresh-dependencies

# Reset Gradle daemon
./gradlew --stop
./gradlew build
```

#### IDE Issues
- **Android Studio**: `File → Invalidate Caches → Invalidate and Restart`
- **VS Code**: `Ctrl+Shift+P → "Java: Clean Workspace"`

### 📁 Project Structure Overview

```
orbot-meshrabiya-integration/
├── app/                          # Main application module
├── integration/                  # Integration testing module
├── orbot-android/               # Orbot submodule
├── Meshrabiya/                  # Meshrabiya submodule
│   └── lib-meshrabiya/         # Core mesh networking library
├── build.gradle.kts            # Root build configuration
├── settings.gradle.kts         # Project settings
├── gradle.properties           # Gradle properties
└── local.properties           # Local environment settings (gitignored)
```

### 🔒 Security Considerations

- **Never commit `local.properties`** - contains sensitive paths
- **Keep API keys and signing configs secure** - use environment variables
- **Review submodule updates** - ensure they don't introduce vulnerabilities

### 📚 Additional Resources

- [Android Developer Documentation](https://developer.android.com/docs)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Gradle Documentation](https://docs.gradle.org/)
- [Git Submodules Guide](https://git-scm.com/book/en/v2/Git-Tools-Submodules)

### 🆘 Getting Help

- **Issues**: Create an issue in the GitHub repository
- **Discussions**: Use GitHub Discussions for questions
- **Code Reviews**: Submit pull requests for review

---

# 📱 Deployment & Emulation Guide

This comprehensive guide provides step-by-step instructions for deploying and testing the Orbot-Meshrabiya integration on both physical devices and emulators. These instructions assume you have completed the developer onboarding setup above.

## 🎯 Prerequisites Checklist

Before proceeding, ensure you have completed:
- ✅ Android Studio or VS Code installation and configuration
- ✅ Android SDK and build tools setup
- ✅ Project successfully built (`./gradlew build` passes)
- ✅ All tests passing (`./gradlew runAllTests`)

---

## 🏗️ Android Studio Deployment

### 📱 Setting Up Android Emulator (Beginner-Friendly)

#### Step 1: Open AVD Manager
1. **Launch Android Studio**
2. **Open your project** (orbot-meshrabiya-integration)
3. **Click "Tools"** in the menu bar
4. **Select "AVD Manager"** (Android Virtual Device Manager)

#### Step 2: Create New Virtual Device
1. **Click "Create Virtual Device"** button
2. **Select a device definition**:
   - **Recommended**: Pixel 7 or Pixel 8 (good performance)
   - **Alternative**: Any device with API 28+ support
3. **Click "Next"**

#### Step 3: Choose System Image
1. **Select "Recommended" tab**
2. **Choose Android API Level**:
   - **Recommended**: API 34 (Android 14) - most stable
   - **Minimum**: API 28 (Android 9) - project minimum
3. **Click "Download"** if not already downloaded
4. **Wait for download** to complete
5. **Click "Next"**

#### Step 4: Configure AVD
1. **Name your AVD**: e.g., "Orbot_Mesh_Test_Device"
2. **Advanced Settings** (optional but recommended):
   - **RAM**: 4096 MB (for better performance)
   - **VM Heap**: 256 MB
   - **Internal Storage**: 2048 MB
   - **SD Card**: 1024 MB (for storage testing)
3. **Click "Finish"**

### 🚀 Deploying to Emulator

#### Step 1: Start Emulator
1. **In AVD Manager**, click the **green play button** next to your device
2. **Wait for emulator** to fully boot (can take 2-3 minutes first time)
3. **Verify emulator is ready**: you should see the Android home screen

#### Step 2: Run the Application
1. **In Android Studio**, click the **green "Run" button** (▶️) in toolbar
2. **Select your emulator** from the device dropdown
   - Should show as "Pixel 7 API 34" (or your chosen device)
3. **Click "OK"**
4. **Wait for build and deployment** (first time can take 5-10 minutes)

#### Step 3: Verify Installation
1. **App should launch automatically** on emulator
2. **Look for "Orbot" icon** in app drawer if it doesn't auto-launch
3. **Check for mesh integration features**:
   - Mesh network toggle
   - Gateway settings
   - Storage sharing options

### 📲 Deploying to Physical Device

#### Step 1: Enable Developer Options
1. **On your Android device**:
   - Go to **Settings > About phone**
   - **Tap "Build number" 7 times** rapidly
   - You'll see "You are now a developer!" message

#### Step 2: Enable USB Debugging
1. **Go to Settings > System > Developer options**
2. **Toggle "USB debugging" ON**
3. **Toggle "Install via USB" ON** (if available)

#### Step 3: Connect Device
1. **Connect phone to computer** via USB cable
2. **On phone**, tap **"Allow USB debugging"** when prompted
3. **Check "Always allow from this computer"**
4. **Tap "OK"**

#### Step 4: Verify Connection
1. **In Android Studio**, check device dropdown
2. **Your device should appear** (e.g., "Samsung Galaxy S23")
3. **If not visible**:
   - Try different USB cable
   - Check USB connection mode (should be "File Transfer" or "MTP")
   - Restart ADB: `Tools > SDK Manager > SDK Tools > Android SDK Platform-Tools`

#### Step 5: Deploy to Device
1. **Select your physical device** from dropdown
2. **Click "Run" button** (▶️)
3. **First deployment** may take several minutes
4. **App will install and launch** on your device

---

## 💻 Visual Studio Code Deployment

### 🔧 Initial Setup Verification

#### Step 1: Verify Extensions
1. **Open VS Code**
2. **Press Ctrl+Shift+X** (Cmd+Shift+X on Mac)
3. **Verify these extensions are installed**:
   - ✅ Extension Pack for Java
   - ✅ Kotlin Language
   - ✅ Gradle for Java
   - ✅ Android iOS Emulator

#### Step 2: Open Project
1. **File > Open Folder**
2. **Select** `orbot-meshrabiya-integration` directory
3. **Wait for Java/Kotlin indexing** to complete (bottom status bar)

### 🏃‍♂️ Using VS Code Tasks for Deployment

#### Step 1: Build Project
1. **Press Ctrl+Shift+P** (Cmd+Shift+P on Mac)
2. **Type**: "Tasks: Run Task"
3. **Select**: "Build Project"
4. **Wait for build** to complete successfully

#### Step 2: Start Emulator (Command Line)
1. **Open integrated terminal**: Ctrl+` (Cmd+` on Mac)
2. **List available AVDs**:
   ```bash
   $ANDROID_HOME/emulator/emulator -list-avds
   ```
3. **Start your emulator**:
   ```bash
   $ANDROID_HOME/emulator/emulator -avd YOUR_AVD_NAME &
   ```
   Replace `YOUR_AVD_NAME` with your emulator name from step 2

#### Step 3: Deploy Application
1. **In VS Code terminal**:
   ```bash
   # Build and install debug APK
   ./gradlew :integration:assembleDebug
   
   # Install to connected device/emulator
   ./gradlew :integration:installDebug
   ```

#### Step 4: Launch Application
1. **Using ADB command**:
   ```bash
   adb shell am start -n org.torproject.android/.OrbotMainActivity
   ```

### 🔍 Advanced VS Code Debugging

#### Step 1: Set Up Debug Configuration
1. **Create `.vscode/launch.json`** (if not exists):
   ```json
   {
       "version": "0.2.0",
       "configurations": [
           {
               "type": "java",
               "name": "Debug Android App",
               "request": "attach",
               "hostName": "localhost",
               "port": 5005,
               "projectName": "integration"
           }
       ]
   }
   ```

#### Step 2: Start Debug Session
1. **Run app in debug mode**:
   ```bash
   ./gradlew :integration:assembleDebug -Pdebug=true
   ```
2. **In VS Code**, press **F5** to start debugging
3. **Set breakpoints** by clicking left margin of code lines

---

## 🧪 Testing Your Deployment

### ✅ Basic Functionality Tests

#### Test 1: Orbot Core Functionality
1. **Launch the app**
2. **Tap the main power button** to start Tor
3. **Wait for "Connected to Tor network"** message
4. **Verify** green connection indicator

#### Test 2: Mesh Integration Features
1. **Look for "Mesh Network" section** in main UI
2. **Try toggling mesh features**:
   - Internet Gateway sharing
   - Tor Gateway sharing
   - Distributed storage
3. **Check for mesh node discovery** (may require multiple devices)

#### Test 3: Gateway Functionality
1. **Enable "Internet Gateway" mode**
2. **Verify SOCKS proxy** is accessible
3. **Test traffic routing** (advanced users)

### 🔧 Troubleshooting Common Issues

#### Issue: "App won't install"
**Solutions**:
- Ensure device has sufficient storage (>100MB free)
- Try uninstalling any existing Orbot versions
- Check USB debugging is enabled
- Try different USB cable/port

#### Issue: "Build failed"
**Solutions**:
- Run `./gradlew clean` then rebuild
- Check Java version: `java -version` (should be 17)
- Verify Android SDK path in `local.properties`
- Update Android build tools if needed

#### Issue: "Emulator is slow"
**Solutions**:
- Increase emulator RAM allocation (AVD Manager > Edit)
- Enable hardware acceleration (Intel HAXM or Hyper-V)
- Close other resource-intensive applications
- Try a newer API level image

#### Issue: "Device not detected"
**Solutions**:
- Enable USB debugging in Developer Options
- Try different USB connection modes
- Install device-specific USB drivers (Windows)
- Restart ADB: `adb kill-server && adb start-server`

### 📊 Performance Monitoring

#### Using Android Studio Profiler
1. **Run app on device/emulator**
2. **View > Tool Windows > Profiler**
3. **Select your app session**
4. **Monitor**:
   - CPU usage during mesh operations
   - Memory usage with storage sharing
   - Network activity during gateway mode

#### Using VS Code Terminal Monitoring
```bash
# Monitor app performance
adb shell top | grep orbot

# Check mesh network logs
adb logcat | grep -i mesh

# Monitor storage operations
adb logcat | grep -i storage
```

---

## 🎓 Next Steps for Novice Developers

### 📚 Recommended Learning Path
1. **Complete basic deployment** (this guide)
2. **Explore mesh networking concepts** (documentation in `/docs`)
3. **Try modifying UI components** (safe starting point)
4. **Study integration test examples** (`/integration/src/test`)
5. **Contribute to documentation** improvements

### 🛠️ Development Workflow
1. **Make small changes** initially
2. **Test on emulator first**, then physical device
3. **Run tests** before committing: `./gradlew runAllTests`
4. **Use version control** branches for experiments
5. **Ask for code reviews** on significant changes

### 🔗 Additional Resources for Mobile Development
- [Android Developer Fundamentals](https://developer.android.com/courses/fundamentals-training/overview-v2)
- [Kotlin for Android Developers](https://kotlinlang.org/docs/android-overview.html)
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Gradle Build System](https://developer.android.com/studio/build)

---

**Happy coding! 🚀**
