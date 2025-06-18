package com.ustadmobile.orbotmeshrabiyaintegration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

val MainActivity.dataStore by preferencesDataStore(name = "mesh_settings")

class MainActivity : AppCompatActivity() {
    
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
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        startButton.setOnClickListener { startMeshNetwork() }
        stopButton.setOnClickListener { stopMeshNetwork() }
        refreshStatusButton.setOnClickListener { refreshNetworkStatus() }
        testConnectivityButton.setOnClickListener { testNetworkConnectivity() }
        
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
                            externalScheduledExecutor = scheduledExecutor
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
        virtualNode?.close()
        scheduledExecutor.shutdown()
    }
}