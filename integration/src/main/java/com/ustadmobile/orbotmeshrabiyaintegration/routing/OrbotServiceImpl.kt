package com.ustadmobile.orbotmeshrabiyaintegration.routing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.ustadmobile.orbotmeshrabiyaintegration.interfaces.TorService
import kotlinx.coroutines.*

/**
 * Implementation of TorService that integrates with Orbot
 * Handles communication with the Orbot Tor service
 */
class OrbotServiceImpl(private val context: Context) : TorService {
    
    companion object {
        private const val TAG = "OrbotServiceImpl"
        
        // Orbot intent actions
        private const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
        private const val ACTION_START = "org.torproject.android.intent.action.START"
        private const val ACTION_STOP = "org.torproject.android.intent.action.STOP"
        
        // Orbot status values
        private const val STATUS_ON = "ON"
        private const val STATUS_OFF = "OFF"
        private const val STATUS_STARTING = "STARTING"
        private const val STATUS_STOPPING = "STOPPING"
        
        // Orbot package name
        private const val ORBOT_PACKAGE = "org.torproject.android"
        
        // SOCKS proxy settings
        private const val DEFAULT_SOCKS_PORT = 9050
        private const val SOCKS_HOST = "127.0.0.1"
        
        // Status check timeout
        private const val STATUS_CHECK_TIMEOUT = 5000L
    }
    
    @Volatile
    private var torStatus = STATUS_OFF
    
    @Volatile
    private var socksPort = DEFAULT_SOCKS_PORT
    
    @Volatile
    private var isReady = false
    
    private var statusReceiver: BroadcastReceiver? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        registerStatusReceiver()
        checkInitialStatus()
    }
    
    override fun isTorReadyForMesh(): Boolean {
        return isReady && torStatus == STATUS_ON && socksPort > 0
    }
    
    override fun isTorRunning(): Boolean {
        return torStatus == STATUS_ON
    }
    
    override fun enableMeshGateway(enabled: Boolean) {
        Log.d(TAG, "enableMeshGateway($enabled)")
        
        if (enabled) {
            if (!isTorRunning()) {
                startTorService()
            }
        } else {
            // Don't stop Tor when disabling mesh gateway
            // Tor might be used by other apps
            Log.d(TAG, "Mesh gateway disabled but keeping Tor running")
        }
    }
    
    /**
     * Get current SOCKS proxy port
     */
    fun getSocksPort(): Int = socksPort
    
    /**
     * Get SOCKS proxy host
     */
    fun getSocksHost(): String = SOCKS_HOST
    
    /**
     * Get current Tor status
     */
    fun getTorStatus(): String = torStatus
    
    private fun registerStatusReceiver() {
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_STATUS -> {
                        val status = intent.getStringExtra("org.torproject.android.intent.extra.STATUS")
                        val port = intent.getIntExtra("org.torproject.android.intent.extra.SOCKS_PROXY_PORT", DEFAULT_SOCKS_PORT)
                        
                        handleStatusUpdate(status, port)
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_STATUS)
        }
        
        try {
            context.registerReceiver(statusReceiver, filter)
            Log.d(TAG, "Registered Orbot status receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register status receiver", e)
        }
    }
    
    private fun handleStatusUpdate(status: String?, port: Int) {
        Log.d(TAG, "Received Orbot status update: $status, port: $port")
        
        if (status != null) {
            torStatus = status
            socksPort = port
            
            // Update readiness based on status
            isReady = when (status) {
                STATUS_ON -> {
                    Log.d(TAG, "Tor is ready for mesh integration")
                    true
                }
                STATUS_STARTING -> {
                    Log.d(TAG, "Tor is starting...")
                    false
                }
                STATUS_STOPPING -> {
                    Log.d(TAG, "Tor is stopping...")
                    false
                }
                STATUS_OFF -> {
                    Log.d(TAG, "Tor is off")
                    false
                }
                else -> {
                    Log.w(TAG, "Unknown Tor status: $status")
                    false
                }
            }
        }
    }
    
    private fun checkInitialStatus() {
        serviceScope.launch {
            try {
                // Request current status from Orbot
                val statusIntent = Intent()
                statusIntent.action = ACTION_STATUS
                statusIntent.setPackage(ORBOT_PACKAGE)
                
                context.sendBroadcast(statusIntent)
                
                // Wait a bit for response
                delay(1000)
                
                // If no response, assume Tor is not running
                if (torStatus == STATUS_OFF) {
                    Log.d(TAG, "No status response from Orbot - assuming not running")
                    isReady = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking initial Tor status", e)
                isReady = false
            }
        }
    }
    
    private fun startTorService() {
        try {
            Log.d(TAG, "Requesting Tor service start")
            
            val startIntent = Intent()
            startIntent.action = ACTION_START
            startIntent.setPackage(ORBOT_PACKAGE)
            
            context.sendBroadcast(startIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Tor service", e)
        }
    }
    
    private fun stopTorService() {
        try {
            Log.d(TAG, "Requesting Tor service stop")
            
            val stopIntent = Intent()
            stopIntent.action = ACTION_STOP
            stopIntent.setPackage(ORBOT_PACKAGE)
            
            context.sendBroadcast(stopIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Tor service", e)
        }
    }
    
    /**
     * Wait for Tor to be ready with timeout
     */
    suspend fun waitForTorReady(timeoutMs: Long = STATUS_CHECK_TIMEOUT): Boolean {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (isTorReadyForMesh()) {
                    return@withContext true
                }
                
                delay(500) // Check every 500ms
            }
            
            Log.w(TAG, "Timeout waiting for Tor to be ready")
            false
        }
    }
    
    /**
     * Check if Orbot is installed
     */
    fun isOrbotInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get Orbot version if installed
     */
    fun getOrbotVersion(): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Test SOCKS proxy connectivity
     */
    suspend fun testSocksConnectivity(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = java.net.Socket()
                socket.soTimeout = 5000
                socket.connect(java.net.InetSocketAddress(SOCKS_HOST, socksPort), 5000)
                socket.close()
                
                Log.d(TAG, "SOCKS proxy connectivity test successful")
                true
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS proxy connectivity test failed", e)
                false
            }
        }
    }
    
    fun cleanup() {
        try {
            statusReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
                statusReceiver = null
            }
            
            serviceScope.cancel()
            
            Log.d(TAG, "OrbotServiceImpl cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
