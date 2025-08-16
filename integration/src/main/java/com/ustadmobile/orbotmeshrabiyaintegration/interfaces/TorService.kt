package com.ustadmobile.orbotmeshrabiyaintegration.interfaces

/**
 * Interface for Tor service functionality
 * This allows the integration module to work with different implementations
 */
interface TorService {
    /**
     * Check if Tor is running and ready for mesh integration
     */
    fun isTorReadyForMesh(): Boolean
    
    /**
     * Check if Tor service is currently running
     */
    fun isTorRunning(): Boolean
    
    /**
     * Enable mesh gateway functionality
     */
    fun enableMeshGateway(enabled: Boolean)
}
