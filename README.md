# Orbot Meshrabiya Integration

A privacy-preserving mesh networking integration for Orbot, enabling secure, decentralized communication and content sharing in challenging network environments.

## Features

- Multi-hop mesh networking with dynamic role assignment
- End-to-end encrypted communications
- Tor network integration
- Privacy-preserving logging
- Intuitive UI with app selection
- Real-time network monitoring

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
