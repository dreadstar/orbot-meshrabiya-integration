package com.ustadmobile.orbotmeshrabiyaintegration.routing

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ustadmobile.orbotmeshrabiyaintegration.interfaces.MeshTrafficRouter
import com.ustadmobile.orbotmeshrabiyaintegration.MeshOrbotIntegrationApp
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * VPN service for mesh gateway functionality
 * Creates a TUN interface to capture packets from mesh nodes and route them
 * through Tor or clearnet via the main Orbot service
 */
class MeshGatewayVpnService : VpnService() {
    
    companion object {
        private const val TAG = "MeshGatewayVpnService"
        private const val TUN_MTU = 1500
        private const val MESH_SUBNET = "10.10.0.0/16"
        private const val GATEWAY_IP = "10.10.0.1"
        
        const val ACTION_START_MESH_GATEWAY = "org.torproject.android.meshrabiya.START_MESH_GATEWAY"
        const val ACTION_STOP_MESH_GATEWAY = "org.torproject.android.meshrabiya.STOP_MESH_GATEWAY"
        const val EXTRA_GATEWAY_MODE = "gateway_mode"
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var meshTrafficRouter: MeshTrafficRouter? = null
    private var packetReaderJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MESH_GATEWAY -> {
                val mode = intent.getSerializableExtra(EXTRA_GATEWAY_MODE) as? MeshTrafficRouter.GatewayMode
                    ?: MeshTrafficRouter.GatewayMode.NONE
                startMeshGateway(mode)
            }
            ACTION_STOP_MESH_GATEWAY -> {
                stopMeshGateway()
            }
        }
        return START_STICKY
    }
    
    private fun startMeshGateway(mode: MeshTrafficRouter.GatewayMode) {
        try {
            Log.i(TAG, "Starting mesh gateway with mode: $mode")
            
            // Create VPN interface for mesh traffic
            val builder = Builder()
                .setMtu(TUN_MTU)
                .addAddress(GATEWAY_IP, 24)  // Gateway IP in mesh subnet
                .addRoute("0.0.0.0", 0)      // Route all traffic through this interface
                .setSession("Mesh Gateway")
            
            // Create pending intent for VPN settings (optional)
            try {
                val settingsIntent = Intent()
                settingsIntent.setClassName(packageName, "${packageName}.MainActivity")
                val pendingIntent = PendingIntent.getActivity(
                    this, 
                    0, 
                    settingsIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setConfigureIntent(pendingIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set configure intent", e)
            }
            
            // Allow all apps to use the mesh gateway
            try {
                // Add routes for mesh subnet
                builder.addRoute("10.10.0.0", 16)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add mesh route", e)
            }
            
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                // Initialize traffic router
                meshTrafficRouter = MeshTrafficRouterImpl(applicationContext)
                meshTrafficRouter?.enableGatewayRouting(mode)
                
                // Start packet reading
                startPacketReading()
                
                Log.i(TAG, "Mesh gateway VPN established successfully")
                createNotification("Mesh Gateway Active", "Routing mesh traffic through $mode")
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mesh gateway", e)
        }
    }
    
    private fun stopMeshGateway() {
        Log.i(TAG, "Stopping mesh gateway")
        
        packetReaderJob?.cancel()
        meshTrafficRouter?.cleanup()
        
        vpnInterface?.close()
        vpnInterface = null
        
        stopForeground(true)
        stopSelf()
    }
    
    private fun startPacketReading() {
        packetReaderJob = serviceScope.launch {
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            
            val packet = ByteArray(TUN_MTU)
            
            try {
                while (isActive && vpnInterface != null) {
                    val length = vpnInput.read(packet)
                    if (length > 0) {
                        // Process incoming packet
                        processPacket(packet.copyOf(length), vpnOutput)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Error reading packets", e)
                }
            } finally {
                try {
                    vpnInput.close()
                    vpnOutput.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing VPN streams", e)
                }
            }
        }
    }
    
    private suspend fun processPacket(packet: ByteArray, vpnOutput: FileOutputStream) {
        try {
            // Parse packet to determine if it's from mesh network
            if (isFromMeshNetwork(packet)) {
                Log.d(TAG, "Processing mesh packet: ${packet.size} bytes")
                
                // Route packet through mesh traffic router
                val success = meshTrafficRouter?.routePacket(packet) ?: false
                
                if (!success) {
                    Log.w(TAG, "Failed to route mesh packet")
                    // Optionally send ICMP unreachable back to source
                }
            } else {
                // Handle non-mesh packets (shouldn't happen in normal operation)
                Log.d(TAG, "Received non-mesh packet, passing through")
                vpnOutput.write(packet)
                vpnOutput.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet", e)
        }
    }
    
    private fun isFromMeshNetwork(packet: ByteArray): Boolean {
        return try {
            if (packet.size < 20) return false // Minimum IP header size
            
                        // Parse source IP from packet
            val srcIpBytes = packet.copyOfRange(12, 16)
            val srcIp = ((srcIpBytes[0].toInt() and 0xFF) shl 24) or
                       ((srcIpBytes[1].toInt() and 0xFF) shl 16) or
                       ((srcIpBytes[2].toInt() and 0xFF) shl 8) or
                       (srcIpBytes[3].toInt() and 0xFF)
            
            // Check if source IP is in mesh subnet (10.10.x.x)
            val meshSubnet = 0x0A0A0000 // 10.10.0.0
            val meshMask = 0xFFFF0000   // 255.255.0.0
            
            (srcIp.toLong() and meshMask) == meshSubnet.toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if packet is from mesh network", e)
            false
        }
    }
    
    private fun createNotification(title: String, content: String) {
        // Create notification for VPN service
        // This would use the same notification system as Orbot
        Log.d(TAG, "Notification: $title - $content")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopMeshGateway()
    }
}
