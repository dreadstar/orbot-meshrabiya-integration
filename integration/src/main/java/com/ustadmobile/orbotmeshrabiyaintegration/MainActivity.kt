package com.ustadmobile.orbotmeshrabiyaintegration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.ustadmobile.meshrabiya.mmcp.MeshRole
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

val MainActivity.dataStore by preferencesDataStore(name = "mesh_settings")

class MainActivity : AppCompatActivity(), GatewayCapabilitiesManager.GatewayCapabilityListener {
    
    private var virtualNode: AndroidVirtualNode? = null
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    
    // Status display elements
    private lateinit var nodeInfoText: TextView
    private lateinit var hotspotInfoText: TextView
    private lateinit var peersInfoText: TextView
    private lateinit var logText: TextView
    private lateinit var refreshStatusButton: Button
    private lateinit var testConnectivityButton: Button
    
    // Gateway capability elements
    private lateinit var shareInternetSwitch: Switch
    private lateinit var shareTorSwitch: Switch
    private lateinit var gatewayStatusText: TextView
    private lateinit var gatewayCapabilitiesManager: GatewayCapabilitiesManager
    
    // Mesh-Orbot integration elements
    private lateinit var integrationApp: MeshOrbotIntegrationApp
    private lateinit var betaTestLogger: BetaTestLogger
    private lateinit var meshRolesText: TextView
    private lateinit var integrationStatusText: TextView
    private lateinit var updateRolesButton: Button
    private lateinit var viewLogsButton: Button
    private lateinit var toggleLoggingButton: Button
    
    private val scheduledExecutor = Executors.newScheduledThreadPool(4)
    private val logMessages = mutableListOf<String>()
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE
    ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        
        // Initialize status display elements
        nodeInfoText = findViewById(R.id.node_info_text)
        hotspotInfoText = findViewById(R.id.hotspot_info_text)
        peersInfoText = findViewById(R.id.peers_info_text)
        logText = findViewById(R.id.log_text)
        refreshStatusButton = findViewById(R.id.refresh_status_button)
        testConnectivityButton = findViewById(R.id.test_connectivity_button)
        
        // Initialize gateway capability elements
        shareInternetSwitch = findViewById(R.id.share_internet_switch)
        shareTorSwitch = findViewById(R.id.share_tor_switch)
        gatewayStatusText = findViewById(R.id.gateway_status_text)
        
        // Initialize mesh-Orbot integration elements (optional - may not exist in layout)
        try {
            meshRolesText = findViewById(R.id.mesh_roles_text)
            integrationStatusText = findViewById(R.id.integration_status_text)
            updateRolesButton = findViewById(R.id.update_roles_button)
            viewLogsButton = findViewById(R.id.view_logs_button)
            toggleLoggingButton = findViewById(R.id.toggle_logging_button)
            setupIntegrationUI()
        } catch (e: Exception) {
            Log.d("MainActivity", "Integration UI elements not found in layout - integration features disabled")
        }
        
        // Initialize gateway capabilities manager
        gatewayCapabilitiesManager = GatewayCapabilitiesManager.getInstance(this)
        gatewayCapabilitiesManager.addListener(this)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        startButton.setOnClickListener { startMeshNetwork() }
        stopButton.setOnClickListener { stopMeshNetwork() }
        refreshStatusButton.setOnClickListener { refreshNetworkStatus() }
        testConnectivityButton.setOnClickListener { testNetworkConnectivity() }
        
        // Setup gateway capability listeners
        shareInternetSwitch.setOnCheckedChangeListener { _, isChecked ->
            gatewayCapabilitiesManager.shareInternet = isChecked
        }
        
        shareTorSwitch.setOnCheckedChangeListener { _, isChecked ->
            gatewayCapabilitiesManager.shareTor = isChecked
        }
        
        // Initialize status displays
        refreshNetworkStatus()
        
        updateUI()
        
        // Check and request permissions
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, 1)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                updateUI()
            } else {
                Toast.makeText(this, "Permissions required for mesh networking", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startMeshNetwork() {
        Log.d("MainActivity", "startMeshNetwork called")
        
        // Check permissions with detailed logging
        val missingPermissions = requiredPermissions.filter { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            Log.d("MainActivity", "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
            !granted
        }
        
        if (missingPermissions.isNotEmpty()) {
            val message = "Missing permissions: ${missingPermissions.joinToString()}"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.w("MainActivity", message)
            return
        }
        
        lifecycleScope.launch {
            try {
                statusText.text = "Initializing mesh network..."
                Log.d("MainActivity", "Starting mesh network initialization...")
                
                if (virtualNode == null) {
                    Log.d("MainActivity", "Creating AndroidVirtualNode...")
                    statusText.text = "Creating virtual node..."
                    
                    try {
                        virtualNode = AndroidVirtualNode(
                            context = this@MainActivity,
                            dataStore = dataStore,
                            scheduledExecutorService = scheduledExecutor
                        )
                        Log.d("MainActivity", "AndroidVirtualNode created successfully")
                    } catch (constructorException: Exception) {
                        // Handle constructor-specific errors
                        val actualConstructorException = if (constructorException is java.lang.reflect.InvocationTargetException && constructorException.cause != null) {
                            constructorException.cause!!
                        } else {
                            constructorException
                        }
                        
                        Log.e("MainActivity", "Failed in AndroidVirtualNode constructor", actualConstructorException)
                        throw RuntimeException("AndroidVirtualNode constructor failed: ${actualConstructorException.message} (${actualConstructorException::class.simpleName})", actualConstructorException)
                    }
                }
                
                Log.d("MainActivity", "Enabling WiFi hotspot...")
                statusText.text = "Starting WiFi hotspot..."
                
                virtualNode?.setWifiHotspotEnabled(
                    enabled = true,
                    preferredBand = ConnectBand.BAND_5GHZ,
                    hotspotType = HotspotType.LOCALONLY_HOTSPOT
                )
                
                statusText.text = "Mesh network started"
                addLogMessage("Mesh network started successfully")
                updateUI()
                Log.d("MainActivity", "Mesh network started successfully")
            } catch (e: Exception) {
                // Unwrap InvocationTargetException to get the real cause
                val actualException = if (e is java.lang.reflect.InvocationTargetException && e.cause != null) {
                    e.cause!!
                } else {
                    e
                }
                
                val errorMessage = actualException.message ?: "Unknown error"
                val detailedError = "Failed to start: $errorMessage (${actualException::class.simpleName})"
                statusText.text = detailedError
                Log.e("MainActivity", "Failed to start mesh network", actualException)
                
                // Show detailed error in Toast for immediate visibility
                Toast.makeText(this@MainActivity, detailedError, Toast.LENGTH_LONG).show()
                
                // Print full stack trace for debugging
                Log.e("MainActivity", "Original exception:", e)
                Log.e("MainActivity", "Actual cause:", actualException)
                e.printStackTrace()
                actualException.printStackTrace()
            }
        }
    }
    
    private fun stopMeshNetwork() {
        lifecycleScope.launch {
            try {
                virtualNode?.setWifiHotspotEnabled(enabled = false, ConnectBand.BAND_5GHZ, HotspotType.LOCALONLY_HOTSPOT)
                virtualNode?.close()
                virtualNode = null
                
                statusText.text = "Mesh network stopped"
                updateUI()
            } catch (e: Exception) {
                statusText.text = "Failed to stop: ${e.message}"
            }
        }
    }
    
    private fun updateUI() {
        val isRunning = virtualNode != null
        startButton.isEnabled = !isRunning && hasRequiredPermissions()
        stopButton.isEnabled = isRunning
        refreshStatusButton.isEnabled = isRunning
        testConnectivityButton.isEnabled = isRunning
        
        if (statusText.text.isEmpty()) {
            statusText.text = when {
                !hasRequiredPermissions() -> "Permissions required for mesh networking"
                isRunning -> "Mesh network running"
                else -> "Mesh network stopped"
            }
        }
        
        // Auto-refresh status if running
        if (isRunning) {
            lifecycleScope.launch {
                delay(2000) // Wait 2 seconds after startup
                refreshNetworkStatus()
            }
        }
    }
    
    private fun refreshNetworkStatus() {
        lifecycleScope.launch {
            try {
                val node = virtualNode
                if (node == null) {
                    nodeInfoText.text = "Node: Not initialized"
                    hotspotInfoText.text = "Hotspot: Not enabled"
                    peersInfoText.text = "Peers: Node not running"
                    return@launch
                }
                
                // Get node information
                val nodeAddress = try {
                    node.address?.hostAddress ?: "Unknown"
                } catch (e: Exception) {
                    "Error getting address: ${e.message}"
                }
                
                val nodePort = try {
                    node.port.toString()
                } catch (e: Exception) {
                    "Unknown"
                }
                
                nodeInfoText.text = "Node: $nodeAddress:$nodePort\nState: Running"
                
                // Get hotspot information
                val hotspotInfo = try {
                    val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
                    val isEnabled = wifiManager.isWifiEnabled
                    "Hotspot: Local-only hotspot\nWiFi State: ${if (isEnabled) "Enabled" else "Disabled"}\nBand: 5GHz preferred"
                } catch (e: Exception) {
                    "Hotspot: Error getting info - ${e.message}"
                }
                hotspotInfoText.text = hotspotInfo
                
                // Get peers information (placeholder for now)
                peersInfoText.text = "Peers: Scanning for mesh peers...\nDiscovered: 0\nConnected: 0"
                
                addLogMessage("Status refreshed")
                
                // Validate gateway capabilities
                gatewayCapabilitiesManager.validateCapabilities()
                
            } catch (e: Exception) {
                addLogMessage("Error refreshing status: ${e.message}")
                Log.e("MainActivity", "Error refreshing status", e)
            }
        }
    }
    
    private fun testNetworkConnectivity() {
        lifecycleScope.launch {
            try {
                addLogMessage("Starting network connectivity test...")
                
                val node = virtualNode
                if (node == null) {
                    addLogMessage("Cannot test: No active node")
                    return@launch
                }
                
                // Test 1: Check node address
                val nodeAddress = node.address?.hostAddress
                addLogMessage("Node address: $nodeAddress")
                
                // Test 2: Check if we can bind to ports
                addLogMessage("Node port: ${node.port}")
                
                // Test 3: WiFi connectivity test
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
                val wifiInfo = wifiManager.connectionInfo
                addLogMessage("WiFi SSID: ${wifiInfo.ssid}")
                addLogMessage("WiFi BSSID: ${wifiInfo.bssid}")
                
                addLogMessage("Connectivity test completed")
                
            } catch (e: Exception) {
                addLogMessage("Connectivity test failed: ${e.message}")
                Log.e("MainActivity", "Connectivity test failed", e)
            }
        }
    }
    
    private fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        
        logMessages.add(logEntry)
        
        // Keep only last 20 messages
        if (logMessages.size > 20) {
            logMessages.removeAt(0)
        }
        
        // Update UI
        runOnUiThread {
            logText.text = "Logs:\n${logMessages.joinToString("\n")}"
        }
        
        Log.d("MainActivity", logEntry)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        gatewayCapabilitiesManager.removeListener(this)
        gatewayCapabilitiesManager.cleanup()
        virtualNode?.close()
        scheduledExecutor.shutdown()
    }
    
    // GatewayCapabilityListener implementation
    override fun onCapabilityChanged(status: GatewayCapabilitiesManager.GatewayStatus) {
        runOnUiThread {
            // Update switch states without triggering listeners
            shareInternetSwitch.setOnCheckedChangeListener(null)
            shareTorSwitch.setOnCheckedChangeListener(null)
            
            shareInternetSwitch.isChecked = status.shareInternet
            shareTorSwitch.isChecked = status.shareTor
            
            // Update switch availability based on system capabilities
            shareInternetSwitch.isEnabled = status.canShareInternet
            shareTorSwitch.isEnabled = status.canShareTor
            
            // Update status text
            gatewayStatusText.text = "Status: ${gatewayCapabilitiesManager.getStatusDescription()}"
            
            // Set status text color based on capabilities
            val statusColor = when {
                status.shareInternet && status.shareTor -> android.graphics.Color.parseColor("#00FFFF") // Cyan
                status.shareInternet -> android.graphics.Color.parseColor("#00FF00") // Green
                status.shareTor -> android.graphics.Color.parseColor("#FF8C00") // Orange
                else -> android.graphics.Color.parseColor("#FFFFFF") // White
            }
            gatewayStatusText.setTextColor(statusColor)
            
            // Re-attach listeners
            shareInternetSwitch.setOnCheckedChangeListener { _, isChecked ->
                gatewayCapabilitiesManager.shareInternet = isChecked
            }
            
            shareTorSwitch.setOnCheckedChangeListener { _, isChecked ->
                gatewayCapabilitiesManager.shareTor = isChecked
            }
            
            // Log capability changes
            val capabilityDescription = when {
                status.shareInternet && status.shareTor -> "dual gateway mode (Internet + Tor)"
                status.shareInternet -> "Internet gateway mode"
                status.shareTor -> "Tor gateway mode"
                else -> "standard node mode"
            }
            
            addLogMessage("Gateway capability: $capabilityDescription")
        }
    }
    
    // ===== MESH-ORBOT INTEGRATION METHODS =====
    
    private fun setupIntegrationUI() {
        try {
            // Initialize integration components
            integrationApp = application as MeshOrbotIntegrationApp
            betaTestLogger = BetaTestLogger.getInstance(this)
            
            // Setup button listeners
            updateRolesButton.setOnClickListener {
                integrationApp.triggerRoleUpdate()
                Toast.makeText(this, "Role update triggered", Toast.LENGTH_SHORT).show()
            }
            
            viewLogsButton.setOnClickListener {
                showBetaLogs()
            }
            
            toggleLoggingButton.setOnClickListener {
                toggleLoggingLevel()
            }
            
            updateLoggingButtonText()
            
            // Start periodic integration status updates
            startIntegrationStatusUpdates()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to setup integration UI", e)
        }
    }
    
    private fun startIntegrationStatusUpdates() {
        lifecycleScope.launch {
            while (true) {
                try {
                    updateIntegrationStatus()
                    delay(3000) // Update every 3 seconds
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error updating integration status", e)
                    delay(5000) // Back off on error
                }
            }
        }
    }
    
    private fun updateIntegrationStatus() {
        try {
            val status = integrationApp.getCurrentStatus()
            
            // Update roles display
            val rolesDisplay = if (status.meshRoles.isEmpty()) {
                "No active roles"
            } else {
                status.meshRoles.joinToString(", ") { role ->
                    when (role) {
                        MeshRole.MESH_PARTICIPANT -> "Participant"
                        MeshRole.MESH_ROUTER -> "Router"
                        MeshRole.TOR_GATEWAY -> "Tor Gateway"
                        MeshRole.CLEARNET_GATEWAY -> "Clearnet Gateway"
                        MeshRole.I2P_GATEWAY -> "I2P Gateway"
                        MeshRole.STORAGE_NODE -> "Storage"
                        MeshRole.COMPUTE_NODE -> "Compute"
                        MeshRole.COORDINATOR -> "Coordinator"
                        else -> role.name.lowercase().replace('_', ' ').capitalize()
                    }
                }
            }
            meshRolesText.text = "Mesh Roles: $rolesDisplay"
            
            // Update integration status
            val integrationInfo = """
                Status: ${status.statusSummary}
                Gateway Active: ${status.isGatewayActive}
                Gateway Mode: ${status.gatewayMode.name}
                Tor Ready: ${status.isTorReady}
                Mesh Nodes: ${status.meshIntelligence.totalNodes}
                Active Gateways: ${status.meshIntelligence.activeGateways}
            """.trimIndent()
            
            integrationStatusText.text = integrationInfo
            
            // Update text color based on gateway status
            val statusColor = when {
                status.isActingAsGateway -> android.graphics.Color.GREEN
                status.isGatewayActive -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.WHITE
            }
            integrationStatusText.setTextColor(statusColor)
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating integration status", e)
            integrationStatusText.text = "Integration status error: ${e.message}"
            integrationStatusText.setTextColor(android.graphics.Color.RED)
        }
    }
    
    private fun showBetaLogs() {
        try {
            val logs = betaTestLogger.getLogs()
            
            if (logs.isEmpty()) {
                Toast.makeText(this, "No logs captured", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show recent logs
            val recentLogs = logs.takeLast(15)
            val logText = recentLogs.joinToString("\n") { log ->
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                "$timestamp [${log.level.name.first()}] ${log.message}"
            }
            
            // Update the existing log display with integration logs
            addLogMessage("=== INTEGRATION LOGS ===")
            logText.split("\n").forEach { line ->
                addLogMessage(line)
            }
            addLogMessage("=== END INTEGRATION LOGS ===")
            
            Toast.makeText(this, "Integration logs added to display (${logs.size} total)", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error viewing logs: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Error showing beta logs", e)
        }
    }
    
    private fun toggleLoggingLevel() {
        try {
            val currentLevel = betaTestLogger.getLogLevel()
            val newLevel = when (currentLevel) {
                LogLevel.DISABLED -> LogLevel.BASIC
                LogLevel.BASIC -> LogLevel.DETAILED
                LogLevel.DETAILED -> LogLevel.FULL
                LogLevel.FULL -> LogLevel.DISABLED
            }
            
            betaTestLogger.setLogLevel(newLevel)
            updateLoggingButtonText()
            
            Toast.makeText(this, "Logging level: ${newLevel.name}", Toast.LENGTH_SHORT).show()
            addLogMessage("Changed logging level to: ${newLevel.name}")
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error changing log level: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Error toggling logging level", e)
        }
    }
    
    private fun updateLoggingButtonText() {
        try {
            val level = betaTestLogger.getLogLevel()
            toggleLoggingButton.text = "Logging: ${level.name}"
        } catch (e: Exception) {
            toggleLoggingButton.text = "Logging: ERROR"
        }
    }
}