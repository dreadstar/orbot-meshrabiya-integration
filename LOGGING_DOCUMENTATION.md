## BetaTestLogger Integration with Consent-Based Log Filtering

I have successfully implemented the integration of BetaTestLogger with consent-based log level filtering as requested. Here's what was accomplished:

### Implementation Summary

#### 1. Updated LogLevel Enum
**File**: `Meshrabiya/lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/beta/LogLevel.kt`

- Added consent-based levels: `DISABLED`, `BASIC`, `DETAILED`, `FULL`
- Maintained standard log levels: `DEBUG`, `INFO`, `WARN`, `ERROR`
- Implemented `shouldLog(consentLevel)` method that enforces filtering rules:
  - **DISABLED**: Only ERROR logs
  - **BASIC**: WARN + ERROR logs
  - **DETAILED**: WARN + ERROR + INFO logs
  - **FULL**: All logs (WARN + ERROR + INFO + DEBUG)

#### 2. Refactored BetaTestLogger
**File**: `Meshrabiya/lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/beta/BetaTestLogger.kt`

- **Singleton Pattern**: Thread-safe getInstance() method
- **Consent Integration**: setLogLevel/getLogLevel methods with SharedPreferences persistence
- **Filtering Logic**: All log methods check `shouldLog()` before capturing logs
- **Android Log Integration**: Dual logging to both memory and Android Log system
- **Memory Management**: Automatic cleanup when logs exceed 10,000 entries

#### 3. Integration with EmergentRoleManager
**File**: `Meshrabiya/lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/EmergentRoleManager.kt`

- Updated to use BetaTestLogger singleton pattern
- All existing logging calls now respect consent-based filtering
- Logs role assignments, mesh intelligence updates, and error conditions

#### 4. Integration with MeshRoleManager
**File**: `Meshrabiya/lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/MeshRoleManager.kt`

- Updated to use BetaTestLogger singleton pattern
- Existing logging infrastructure now consent-aware

### Key Features Implemented

1. **Consent-Based Filtering**: The system respects user consent from BetaConsentActivity:
   - DISABLED → Only ERROR logs captured
   - BASIC → WARN + ERROR logs captured
   - DETAILED → WARN + ERROR + INFO logs captured
   - FULL → All logs including DEBUG captured

2. **Real-time Control**: Log level can be changed at runtime and persists across app restarts

3. **Performance Optimized**: Filtering happens before log creation to minimize overhead

4. **Thread Safe**: Concurrent access supported with proper synchronization

5. **Android Integration**: Logs appear in both the custom system and standard Android logcat

### Usage Example

```kotlin
// Get logger instance
val logger = BetaTestLogger.getInstance(context)

// Set consent level (from BetaConsentActivity)
logger.setLogLevel(LogLevel.DETAILED)

// Log messages - only WARN, ERROR, INFO will be captured
logger.log(LogLevel.DEBUG, "Debug message") // Filtered out
logger.log(LogLevel.INFO, "Info message")   // Captured
logger.log(LogLevel.WARN, "Warning message") // Captured
logger.log(LogLevel.ERROR, "Error message")  // Captured

// Get filtered logs based on current consent level
val logs = logger.getLogs()
```

### Integration Points

The system is ready to be integrated with `BetaConsentActivity`. When the user changes their consent preference, simply call:

```kotlin
BetaTestLogger.getInstance(context).setLogLevel(selectedLevel)
```

This will immediately affect all subsequent logging throughout the mesh networking system, including:
- EmergentRoleManager role assignments
- MeshRoleManager decisions
- All other components using the BetaTestLogger

### Files Created/Modified

1. **LogLevel.kt** - Enhanced with consent levels and filtering logic
2. **BetaTestLogger.kt** - Complete rewrite with singleton pattern and consent filtering
3. **LogEntry.kt** - Data class for structured logging
4. **EmergentRoleManager.kt** - Updated to use new logger singleton
5. **MeshRoleManager.kt** - Updated to use new logger singleton
6. **EmergentRoleManagerSimpleIntegrationTest.kt** - Added comprehensive test coverage

The implementation provides the exact filtering behavior requested:
- **DISABLED**: BetaTestLogger logs only ERROR messages
- **BASIC**: BetaTestLogger logs WARN and ERROR messages  
- **DETAILED**: BetaTestLogger logs WARN, ERROR, INFO, and FULL messages

All logging infrastructure in the mesh networking system now respects these consent levels, providing users with granular control over their data sharing preferences.
