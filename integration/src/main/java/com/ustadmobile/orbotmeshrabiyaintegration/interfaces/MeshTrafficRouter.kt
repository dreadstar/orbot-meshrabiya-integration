package com.ustadmobile.orbotmeshrabiyaintegration.interfaces

/**
 * Interface for mesh traffic routing functionality
 */
interface MeshTrafficRouter {
    
    enum class GatewayMode {
        NONE,
        CLEARNET_GATEWAY,
        TOR_GATEWAY
    }
    
    /**
     * Enable gateway routing with specified mode
     */
    fun enableGatewayRouting(mode: GatewayMode)
    
    /**
     * Check if gateway routing is currently active
     */
    fun isGatewayActive(): Boolean
    
    /**
     * Get current gateway mode
     */
    fun getCurrentGatewayMode(): GatewayMode
    
    /**
     * Route a packet through the mesh/gateway system
     */
    fun routePacket(packet: ByteArray): Boolean
    
    /**
     * Cleanup routing resources
     */
    fun cleanup()
}
