package com.ustadmobile.orbotmeshrabiyaintegration.config

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/**
 * Manages service configuration and Android manifest updates
 * for mesh gateway VPN service integration
 */
class MeshServiceConfiguration(private val context: Context) {
    
    companion object {
        private const val TAG = "MeshServiceConfig"
        
        // Required permissions for mesh gateway functionality
        val REQUIRED_PERMISSIONS = arrayOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.BIND_VPN_SERVICE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.WAKE_LOCK"
        )
        
        // VPN service configuration
        const val VPN_SERVICE_CLASS = "com.ustadmobile.orbotmeshrabiyaintegration.routing.MeshGatewayVpnService"
        const val VPN_SERVICE_ACTION = "android.net.VpnService"
    }
    
    /**
     * Check if all required permissions are declared in the manifest
     */
    fun checkPermissions(): PermissionStatus {
        val packageInfo = try {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get package info", e)
            return PermissionStatus(false, emptyList(), REQUIRED_PERMISSIONS.toList())
        }
        
        val declaredPermissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()
        val missingPermissions = REQUIRED_PERMISSIONS.filter { it !in declaredPermissions }
        
        Log.d(TAG, "Declared permissions: ${declaredPermissions.size}")
        Log.d(TAG, "Required permissions: ${REQUIRED_PERMISSIONS.size}")
        Log.d(TAG, "Missing permissions: ${missingPermissions.size}")
        
        return PermissionStatus(
            hasAllPermissions = missingPermissions.isEmpty(),
            declaredPermissions = declaredPermissions.toList(),
            missingPermissions = missingPermissions
        )
    }
    
    /**
     * Check if VPN service is properly declared in manifest
     */
    fun checkVpnServiceDeclaration(): ServiceStatus {
        val packageInfo = try {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SERVICES
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get service info", e)
            return ServiceStatus(false, "Failed to read package info")
        }
        
        val services = packageInfo.services ?: emptyArray()
        val vpnService = services.find { 
            it.name == VPN_SERVICE_CLASS 
        }
        
        return if (vpnService != null) {
            val hasVpnPermission = vpnService.permission == "android.permission.BIND_VPN_SERVICE"
            ServiceStatus(
                isConfigured = hasVpnPermission,
                message = if (hasVpnPermission) {
                    "VPN service properly configured"
                } else {
                    "VPN service missing BIND_VPN_SERVICE permission"
                }
            )
        } else {
            ServiceStatus(false, "VPN service not declared in manifest")
        }
    }
    
    /**
     * Generate Android manifest entries for mesh gateway services
     */
    fun generateManifestEntries(): String {
        return """
<!-- Mesh Gateway Integration Permissions -->
${REQUIRED_PERMISSIONS.joinToString("\n") { "    <uses-permission android:name=\"$it\" />" }}

<!-- VPN Service Declaration -->
<service
    android:name="$VPN_SERVICE_CLASS"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="$VPN_SERVICE_ACTION" />
    </intent-filter>
</service>

<!-- Mesh Traffic Router Service -->
<service
    android:name="com.ustadmobile.orbotmeshrabiyaintegration.routing.MeshTrafficRouterService"
    android:enabled="true"
    android:exported="false">
</service>

<!-- Beta Test Logger Service -->
<service
    android:name="com.ustadmobile.orbotmeshrabiyaintegration.testing.BetaTestLogger"
    android:enabled="true"
    android:exported="false">
</service>

<!-- Network State Receiver -->
<receiver
    android:name="com.ustadmobile.orbotmeshrabiyaintegration.routing.NetworkStateReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter android:priority="1000">
        <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
        <action android:name="android.net.wifi.STATE_CHANGE" />
        <action android:name="android.net.wifi.WIFI_STATE_CHANGED" />
    </intent-filter>
</receiver>

<!-- Orbot Integration Receiver -->
<receiver
    android:name="com.ustadmobile.orbotmeshrabiyaintegration.routing.OrbotStateReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="org.torproject.android.intent.action.STATUS" />
        <action android:name="org.torproject.android.intent.action.START" />
        <action android:name="org.torproject.android.intent.action.STOP" />
    </intent-filter>
</receiver>

<!-- File Provider for sharing logs and configurations -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${context.packageName}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
        """.trimIndent()
    }
    
    /**
     * Generate file_paths.xml for FileProvider
     */
    fun generateFileProviderPaths(): String {
        return """
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Mesh configuration files -->
    <files-path name="mesh_config" path="mesh/" />
    
    <!-- Log files -->
    <files-path name="logs" path="logs/" />
    
    <!-- Cache directory for temporary files -->
    <cache-path name="cache" path="." />
    
    <!-- External storage for exports -->
    <external-files-path name="external_files" path="." />
</paths>
        """.trimIndent()
    }
    
    /**
     * Create necessary directories for mesh gateway operation
     */
    fun createRequiredDirectories(): DirectoryStatus {
        val requiredDirs = listOf(
            File(context.filesDir, "mesh"),
            File(context.filesDir, "logs"),
            File(context.cacheDir, "mesh_temp"),
            File(context.getExternalFilesDir(null), "mesh_exports")
        )
        
        val createdDirs = mutableListOf<String>()
        val failedDirs = mutableListOf<String>()
        
        for (dir in requiredDirs) {
            try {
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        createdDirs.add(dir.absolutePath)
                        Log.d(TAG, "Created directory: ${dir.absolutePath}")
                    } else {
                        failedDirs.add(dir.absolutePath)
                        Log.e(TAG, "Failed to create directory: ${dir.absolutePath}")
                    }
                } else {
                    Log.d(TAG, "Directory already exists: ${dir.absolutePath}")
                }
            } catch (e: Exception) {
                failedDirs.add(dir.absolutePath)
                Log.e(TAG, "Exception creating directory: ${dir.absolutePath}", e)
            }
        }
        
        return DirectoryStatus(
            success = failedDirs.isEmpty(),
            createdDirectories = createdDirs,
            failedDirectories = failedDirs
        )
    }
    
    /**
     * Generate integration configuration summary
     */
    fun generateConfigurationSummary(): ConfigurationSummary {
        val permissionStatus = checkPermissions()
        val serviceStatus = checkVpnServiceDeclaration()
        val directoryStatus = createRequiredDirectories()
        
        return ConfigurationSummary(
            permissionStatus = permissionStatus,
            serviceStatus = serviceStatus,
            directoryStatus = directoryStatus,
            manifestEntries = generateManifestEntries(),
            fileProviderPaths = generateFileProviderPaths()
        )
    }
    
    /**
     * Check if Orbot is installed and accessible
     */
    fun checkOrbotAvailability(): OrbotStatus {
        return try {
            val orbotPackage = "org.torproject.android"
            val packageInfo = context.packageManager.getPackageInfo(orbotPackage, 0)
            
            OrbotStatus(
                isInstalled = true,
                version = packageInfo.versionName ?: "unknown",
                versionCode = packageInfo.versionCode,
                packageName = orbotPackage
            )
        } catch (e: PackageManager.NameNotFoundException) {
            OrbotStatus(
                isInstalled = false,
                version = null,
                versionCode = 0,
                packageName = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Orbot availability", e)
            OrbotStatus(
                isInstalled = false,
                version = null,
                versionCode = 0,
                packageName = null
            )
        }
    }
}

/**
 * Data classes for configuration status
 */
data class PermissionStatus(
    val hasAllPermissions: Boolean,
    val declaredPermissions: List<String>,
    val missingPermissions: List<String>
)

data class ServiceStatus(
    val isConfigured: Boolean,
    val message: String
)

data class DirectoryStatus(
    val success: Boolean,
    val createdDirectories: List<String>,
    val failedDirectories: List<String>
)

data class OrbotStatus(
    val isInstalled: Boolean,
    val version: String?,
    val versionCode: Int,
    val packageName: String?
)

data class ConfigurationSummary(
    val permissionStatus: PermissionStatus,
    val serviceStatus: ServiceStatus,
    val directoryStatus: DirectoryStatus,
    val manifestEntries: String,
    val fileProviderPaths: String
) {
    val isFullyConfigured: Boolean
        get() = permissionStatus.hasAllPermissions && 
                serviceStatus.isConfigured && 
                directoryStatus.success
}
