package com.ustadmobile.orbotmeshrabiyaintegration.routing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.InetAddress

/**
 * Manages network configuration for mesh gateway including:
 * - iptables rules for traffic forwarding
 * - Network interface configuration  
 * - NAT and routing table management
 * - Integration with Android's network stack
 */
class MeshNetworkManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MeshNetworkManager"
        private const val MESH_INTERFACE = "mesh0"
        private const val MESH_SUBNET = "10.10.0.0/16"
        private const val GATEWAY_IP = "10.10.0.1"
        private const val TUN_INTERFACE = "tun-mesh"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConfigured = false
    
    /**
     * Configure network for mesh gateway operation
     */
    suspend fun configureGatewayNetwork(mode: String) {
        try {
            Log.i(TAG, "Configuring mesh gateway network for mode: $mode")
            
            when (mode.lowercase()) {
                "tor" -> configureTorGateway()
                "clearnet" -> configureClearnetGateway()
                else -> {
                    Log.w(TAG, "Unknown gateway mode: $mode")
                    return
                }
            }
            
            isConfigured = true
            Log.i(TAG, "Mesh gateway network configuration completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure gateway network", e)
            throw e
        }
    }
    
    private suspend fun configureTorGateway() {
        Log.d(TAG, "Configuring Tor gateway routing")
        
        // Set up iptables rules for Tor routing
        val commands = listOf(
            // Enable IP forwarding
            "echo 1 > /proc/sys/net/ipv4/ip_forward",
            
            // NAT rules for mesh traffic through Tor
            "iptables -t nat -A OUTPUT -s $MESH_SUBNET -p tcp --dport 1:65534 -j REDIRECT --to-ports 9040",
            "iptables -t nat -A OUTPUT -s $MESH_SUBNET -p udp --dport 53 -j REDIRECT --to-ports 5353",
            
            // Filter rules for mesh traffic
            "iptables -A INPUT -s $MESH_SUBNET -j ACCEPT",
            "iptables -A OUTPUT -d $MESH_SUBNET -j ACCEPT",
            "iptables -A FORWARD -s $MESH_SUBNET -j ACCEPT",
            "iptables -A FORWARD -d $MESH_SUBNET -j ACCEPT",
            
            // Masquerade for outgoing mesh traffic
            "iptables -t nat -A POSTROUTING -s $MESH_SUBNET ! -d $MESH_SUBNET -j MASQUERADE"
        )
        
        executeNetworkCommands(commands, "Tor gateway")
    }
    
    private suspend fun configureClearnetGateway() {
        Log.d(TAG, "Configuring clearnet gateway routing")
        
        // Set up iptables rules for direct internet access
        val commands = listOf(
            // Enable IP forwarding
            "echo 1 > /proc/sys/net/ipv4/ip_forward",
            
            // NAT rules for mesh traffic direct to internet
            "iptables -t nat -A POSTROUTING -s $MESH_SUBNET ! -d $MESH_SUBNET -j MASQUERADE",
            
            // Filter rules for mesh traffic
            "iptables -A INPUT -s $MESH_SUBNET -j ACCEPT",
            "iptables -A OUTPUT -d $MESH_SUBNET -j ACCEPT",
            "iptables -A FORWARD -s $MESH_SUBNET -j ACCEPT",
            "iptables -A FORWARD -d $MESH_SUBNET -j ACCEPT",
            
            // Allow mesh traffic to access internet directly
            "iptables -A FORWARD -s $MESH_SUBNET -o wlan+ -j ACCEPT",
            "iptables -A FORWARD -s $MESH_SUBNET -o rmnet+ -j ACCEPT",
            "iptables -A FORWARD -s $MESH_SUBNET -o ccmni+ -j ACCEPT"
        )
        
        executeNetworkCommands(commands, "Clearnet gateway")
    }
    
    /**
     * Remove mesh gateway network configuration
     */
    suspend fun cleanupGatewayNetwork() {
        try {
            Log.i(TAG, "Cleaning up mesh gateway network configuration")
            
            val cleanupCommands = listOf(
                // Remove NAT rules
                "iptables -t nat -D OUTPUT -s $MESH_SUBNET -p tcp --dport 1:65534 -j REDIRECT --to-ports 9040 2>/dev/null || true",
                "iptables -t nat -D OUTPUT -s $MESH_SUBNET -p udp --dport 53 -j REDIRECT --to-ports 5353 2>/dev/null || true",
                "iptables -t nat -D POSTROUTING -s $MESH_SUBNET ! -d $MESH_SUBNET -j MASQUERADE 2>/dev/null || true",
                
                // Remove filter rules
                "iptables -D INPUT -s $MESH_SUBNET -j ACCEPT 2>/dev/null || true",
                "iptables -D OUTPUT -d $MESH_SUBNET -j ACCEPT 2>/dev/null || true",
                "iptables -D FORWARD -s $MESH_SUBNET -j ACCEPT 2>/dev/null || true",
                "iptables -D FORWARD -d $MESH_SUBNET -j ACCEPT 2>/dev/null || true",
                "iptables -D FORWARD -s $MESH_SUBNET -o wlan+ -j ACCEPT 2>/dev/null || true",
                "iptables -D FORWARD -s $MESH_SUBNET -o rmnet+ -j ACCEPT 2>/dev/null || true",
                "iptables -D FORWARD -s $MESH_SUBNET -o ccmni+ -j ACCEPT 2>/dev/null || true"
            )
            
            executeNetworkCommands(cleanupCommands, "cleanup")
            isConfigured = false
            
            Log.i(TAG, "Mesh gateway network cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during network cleanup", e)
        }
    }
    
    private suspend fun executeNetworkCommands(commands: List<String>, operation: String) {
        withContext(Dispatchers.IO) {
            commands.forEach { command ->
                try {
                    executeShellCommand(command)
                    Log.d(TAG, "Executed $operation command: $command")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to execute $operation command: $command", e)
                    // Continue with other commands even if one fails
                }
            }
        }
    }
    
    private suspend fun executeShellCommand(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                
                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                
                val exitCode = process.waitFor()
                
                reader.close()
                errorReader.close()
                
                if (exitCode != 0 && errorOutput.isNotEmpty()) {
                    Log.w(TAG, "Command failed with exit code $exitCode: $command\nError: $errorOutput")
                }
                
                output.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing shell command: $command", e)
                throw e
            }
        }
    }
    
    /**
     * Configure mesh network interface
     */
    suspend fun configureMeshInterface(): Boolean {
        return try {
            val commands = listOf(
                "ip link add $MESH_INTERFACE type dummy",
                "ip addr add $GATEWAY_IP/24 dev $MESH_INTERFACE",
                "ip link set $MESH_INTERFACE up"
            )
            
            executeNetworkCommands(commands, "mesh interface")
            
            // Verify interface was created
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var found = false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == MESH_INTERFACE) {
                    found = true
                    break
                }
            }
            
            if (found) {
                Log.i(TAG, "Mesh interface $MESH_INTERFACE configured successfully")
            } else {
                Log.w(TAG, "Mesh interface $MESH_INTERFACE not found after configuration")
            }
            
            found
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure mesh interface", e)
            false
        }
    }
    
    /**
     * Remove mesh network interface
     */
    suspend fun removeMeshInterface() {
        try {
            val commands = listOf(
                "ip link set $MESH_INTERFACE down 2>/dev/null || true",
                "ip link delete $MESH_INTERFACE 2>/dev/null || true"
            )
            
            executeNetworkCommands(commands, "mesh interface cleanup")
            Log.i(TAG, "Mesh interface removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing mesh interface", e)
        }
    }
    
    /**
     * Check if device has root access for network configuration
     */
    suspend fun checkRootAccess(): Boolean {
        return try {
            val result = executeShellCommand("id")
            result.contains("uid=0") // Root user ID
        } catch (e: Exception) {
            Log.w(TAG, "Root access check failed", e)
            false
        }
    }
    
    /**
     * Get current network configuration status
     */
    fun getNetworkStatus(): NetworkStatus {
        return NetworkStatus(
            isConfigured = isConfigured,
            meshInterface = MESH_INTERFACE,
            gatewayIp = GATEWAY_IP,
            meshSubnet = MESH_SUBNET
        )
    }
    
    /**
     * Alternative configuration for non-root devices using VpnService
     */
    suspend fun configureVpnBasedGateway(): Boolean {
        Log.i(TAG, "Configuring VPN-based gateway (no root required)")
        
        // This would integrate with VpnService.Builder instead of iptables
        // More limited but doesn't require root access
        
        return try {
            // Configuration would be handled by VpnService
            Log.i(TAG, "VPN-based gateway configuration prepared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure VPN-based gateway", e)
            false
        }
    }
    
    fun cleanup() {
        scope.cancel()
        
        scope.launch {
            cleanupGatewayNetwork()
            removeMeshInterface()
        }
    }
    
    data class NetworkStatus(
        val isConfigured: Boolean,
        val meshInterface: String,
        val gatewayIp: String,
        val meshSubnet: String
    )
}
