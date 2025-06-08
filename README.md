# Orbot Meshrabiya Integration

A privacy-preserving mesh networking integration for Orbot, enabling secure, decentralized communication and content sharing in challenging network environments.

## 🌟 Features

### Mesh Networking
- **Multi-hop Communication**: Route messages through multiple nodes to reach distant peers
- **Dynamic Role Assignment**: Automatic node role optimization based on network conditions
- **Self-healing Network**: Automatic recovery from node failures and network partitions
- **Bandwidth Optimization**: Efficient message routing and data transfer

### Privacy & Security
- **End-to-end Encryption**: All mesh communications are encrypted
- **Tor Integration**: Optional traffic routing through the Tor network
- **Anonymized Logging**: Privacy-preserving diagnostic capabilities
- **Secure Key Exchange**: Protected node-to-node communication

### User Experience
- **Intuitive UI**: Simple mesh network control interface
- **App Selection**: Choose which apps use the mesh network
- **Status Monitoring**: Real-time network health indicators
- **Beta Testing**: Optional enhanced logging for development

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

## 📄 License

This project is licensed under the GPL v3 License - see the LICENSE file for details.

## 🙏 Acknowledgments

- Orbot Project for the base VPN functionality
- Meshrabiya Project for the mesh networking implementation
- All contributors and testers
