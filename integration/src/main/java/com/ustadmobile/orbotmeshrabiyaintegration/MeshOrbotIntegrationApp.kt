package com.ustadmobile.orbotmeshrabiyaintegration

import android.app.Application
import android.content.Context
import android.util.Log
import com.ustadmobile.meshrabiya.vnet.*
import com.ustadmobile.meshrabiya.mmcp.*
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

/**
 * Example application demonstrating mesh-to-Orbot traffic routing integration
 * This shows how to use the EmergentRoleManager with MeshTrafficRouter for
 * automatic gateway functionality
 */
class MeshOrbotIntegrationApp : Application() {
    
    private lateinit var androidVirtualNode: AndroidVirtualNode
    private lateinit var meshRoleManager: MeshRoleManager
    private lateinit var emergentRoleManager: EmergentRoleManager
    private lateinit var meshTrafficRouter: MeshTrafficRouter
    private lateinit var betaTestLogger: BetaTestLogger
    private lateinit var orbotService: OrbotService
    
    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing Mesh-Orbot Integration Application")
        
        // Initialize beta logging
        initializeBetaLogging()
        
        // Initialize core components
        initializeCoreComponents()
        
        // Setup integration
        setupMeshOrbotIntegration()
        
        // Start monitoring and management
        startIntegrationManagement()
        
        Log.d(TAG, "Mesh-Orbot Integration Application initialized successfully")
    }
    
    private fun initializeBetaLogging() {
        betaTestLogger = BetaTestLogger.getInstance(this)
        // Set logging level based on debug build or user preference
        val logLevel = if (BuildConfig.DEBUG) LogLevel.FULL else LogLevel.BASIC
        betaTestLogger.setLogLevel(logLevel)
        
        betaTestLogger.log(LogLevel.INFO, TAG, "Beta logging initialized with level: $logLevel")
    }
    
    private fun initializeCoreComponents() {
        try {
            // Initialize Orbot service connection
            orbotService = OrbotService() // This would be dependency injected in real app
            
            // Initialize mesh components
            androidVirtualNode = AndroidVirtualNode(this)
            meshRoleManager = MeshRoleManager(this, androidVirtualNode)
            
            // Initialize traffic router with Orbot integration
            meshTrafficRouter = MeshTrafficRouter(this, orbotService)
            
            // Initialize emergent role manager with traffic router
            emergentRoleManager = EmergentRoleManager(
                virtualNode = androidVirtualNode,
                context = this,
                meshRoleManager = meshRoleManager,
                meshTrafficRouter = meshTrafficRouter
            )
            
            betaTestLogger.log(LogLevel.INFO, TAG, "Core components initialized successfully")
            
        } catch (e: Exception) {
            betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to initialize core components: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupMeshOrbotIntegration() {
        try {
            // Configure mesh node for gateway capability
            setupMeshNodeForGateway()
            
            // Setup role change monitoring
            setupRoleChangeMonitoring()
            
            // Setup mesh intelligence monitoring
            setupMeshIntelligenceMonitoring()
            
            betaTestLogger.log(LogLevel.INFO, TAG, "Mesh-Orbot integration setup completed")
            
        } catch (e: Exception) {
            betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to setup integration: ${e.message}", e)
        }
    }
    
    private fun setupMeshNodeForGateway() {
        // Enable user preference for Tor proxy
        meshRoleManager.userAllowsTorProxy = true
        
        // Set preferred roles to include gateway capabilities
        val preferredRoles = setOf(
            MeshRole.MESH_PARTICIPANT,
            MeshRole.MESH_ROUTER,
            MeshRole.TOR_GATEWAY // User prefers to act as Tor gateway when capable
        )
        emergentRoleManager.setPreferredRoles(preferredRoles)
        
        betaTestLogger.log(LogLevel.INFO, TAG, "Mesh node configured for gateway capability")
    }
    
    private fun setupRoleChangeMonitoring() {
        applicationScope.launch {
            emergentRoleManager.currentMeshRoles.collect { roles ->
                betaTestLogger.log(LogLevel.INFO, TAG, "Mesh roles updated: $roles")
                
                // Handle specific role changes
                when {
                    MeshRole.TOR_GATEWAY in roles -> {
                        handleTorGatewayRoleAcquired()
                    }
                    MeshRole.CLEARNET_GATEWAY in roles -> {
                        handleClearnetGatewayRoleAcquired()
                    }
                    !hasGatewayRole(roles) -> {
                        handleGatewayRoleRemoved()
                    }
                }
            }
        }
    }
    
    private fun setupMeshIntelligenceMonitoring() {
        applicationScope.launch {
            emergentRoleManager.meshIntelligence.collect { intelligence ->
                betaTestLogger.log(LogLevel.DEBUG, TAG, 
                    "Mesh intelligence updated: ${intelligence.totalNodes} nodes, " +
                    "${intelligence.activeGateways} gateways, load=${intelligence.networkLoad}")
                
                // Proactively update roles based on mesh changes
                updateRolesBasedOnMeshIntelligence(intelligence)
            }
        }
    }
    
    private fun startIntegrationManagement() {
        applicationScope.launch {
            // Periodic role updates
            while (isActive) {
                try {
                    emergentRoleManager.updateRoles()
                    delay(30_000) // Update every 30 seconds
                } catch (e: Exception) {
                    betaTestLogger.log(LogLevel.ERROR, TAG, 
                        "Error in periodic role update: ${e.message}", e)
                    delay(60_000) // Back off on error
                }
            }
        }
        
        // Monitor network connectivity changes
        applicationScope.launch {
            monitorNetworkConnectivity()
        }
        
        // Monitor Orbot service availability
        applicationScope.launch {
            monitorOrbotServiceAvailability()
        }
    }
    
    private fun handleTorGatewayRoleAcquired() {
        betaTestLogger.log(LogLevel.INFO, TAG, "Acquired Tor gateway role - enabling Tor routing")
        
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Ensure Orbot is ready
                if (orbotService.isTorReadyForMesh()) {
                    meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
                    betaTestLogger.log(LogLevel.INFO, TAG, "Tor gateway routing enabled successfully")
                } else {
                    betaTestLogger.log(LogLevel.WARN, TAG, "Orbot not ready for mesh integration")
                }
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to enable Tor gateway: ${e.message}", e)
            }
        }
    }
    
    private fun handleClearnetGatewayRoleAcquired() {
        betaTestLogger.log(LogLevel.INFO, TAG, "Acquired clearnet gateway role - enabling direct routing")
        
        applicationScope.launch(Dispatchers.IO) {
            try {
                meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT)
                betaTestLogger.log(LogLevel.INFO, TAG, "Clearnet gateway routing enabled successfully")
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to enable clearnet gateway: ${e.message}", e)
            }
        }
    }
    
    private fun handleGatewayRoleRemoved() {
        betaTestLogger.log(LogLevel.INFO, TAG, "Gateway role removed - disabling gateway routing")
        
        applicationScope.launch(Dispatchers.IO) {
            try {
                meshTrafficRouter.disableGatewayRouting()
                betaTestLogger.log(LogLevel.INFO, TAG, "Gateway routing disabled successfully")
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to disable gateway routing: ${e.message}", e)
            }
        }
    }
    
    private fun hasGatewayRole(roles: Set<MeshRole>): Boolean {
        return roles.any { it in setOf(MeshRole.TOR_GATEWAY, MeshRole.CLEARNET_GATEWAY, MeshRole.I2P_GATEWAY) }
    }
    
    private fun updateRolesBasedOnMeshIntelligence(intelligence: MeshIntelligence) {
        applicationScope.launch {
            try {
                // If mesh desperately needs gateways and we're capable, offer to help
                if (intelligence.needsMoreGateways && intelligence.totalNodes > 5) {
                    val currentRoles = emergentRoleManager.getCurrentMeshRoles()
                    if (!hasGatewayRole(currentRoles)) {
                        betaTestLogger.log(LogLevel.INFO, TAG, 
                            "Mesh needs gateways - volunteering for gateway role")
                        // The role manager will handle this in the next update cycle
                    }
                }
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, 
                    "Error updating roles based on mesh intelligence: ${e.message}", e)
            }
        }
    }
    
    private suspend fun monitorNetworkConnectivity() {
        while (currentCoroutineContext().isActive) {
            try {
                // Check if we still have internet connectivity for gateway role
                val hasInternet = checkInternetConnectivity()
                val currentRoles = emergentRoleManager.getCurrentMeshRoles()
                
                if (!hasInternet && hasGatewayRole(currentRoles)) {
                    betaTestLogger.log(LogLevel.WARN, TAG, 
                        "Lost internet connectivity - may need to relinquish gateway role")
                    // The role manager will detect this and adjust roles accordingly
                }
                
                delay(60_000) // Check every minute
                
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, 
                    "Error monitoring network connectivity: ${e.message}", e)
                delay(120_000) // Back off on error
            }
        }
    }
    
    private suspend fun monitorOrbotServiceAvailability() {
        while (currentCoroutineContext().isActive) {
            try {
                val isTorReady = orbotService.isTorReadyForMesh()
                val currentRoles = emergentRoleManager.getCurrentMeshRoles()
                
                if (!isTorReady && MeshRole.TOR_GATEWAY in currentRoles) {
                    betaTestLogger.log(LogLevel.WARN, TAG, 
                        "Tor service not ready - may need to switch gateway mode")
                    
                    // Try switching to clearnet gateway mode
                    if (checkInternetConnectivity()) {
                        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT)
                        betaTestLogger.log(LogLevel.INFO, TAG, "Switched to clearnet gateway mode")
                    }
                }
                
                delay(30_000) // Check every 30 seconds
                
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, 
                    "Error monitoring Orbot service: ${e.message}", e)
                delay(60_000) // Back off on error
            }
        }
    }
    
    private suspend fun checkInternetConnectivity(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Simple connectivity check - in real app would be more sophisticated
                val process = Runtime.getRuntime().exec("ping -c 1 8.8.8.8")
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Public API for manually triggering role updates
     */
    fun triggerRoleUpdate() {
        applicationScope.launch {
            try {
                emergentRoleManager.updateRoles()
                betaTestLogger.log(LogLevel.INFO, TAG, "Manual role update triggered")
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Manual role update failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Public API for getting current status
     */
    fun getCurrentStatus(): IntegrationStatus {
        return IntegrationStatus(
            meshRoles = emergentRoleManager.getCurrentMeshRoles(),
            isGatewayActive = meshTrafficRouter.isGatewayRoutingEnabled(),
            gatewayMode = meshTrafficRouter.getCurrentRoutingMode(),
            meshIntelligence = emergentRoleManager.getMeshIntelligence(),
            isTorReady = orbotService.isTorReadyForMesh()
        )
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        betaTestLogger.log(LogLevel.INFO, TAG, "Application terminating - cleaning up")
        
        // Cleanup integration
        applicationScope.launch {
            try {
                meshTrafficRouter.disableGatewayRouting()
                applicationScope.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "MeshOrbotIntegrationApp"
    }
}

/**
 * Status information for the integration
 */
data class IntegrationStatus(
    val meshRoles: Set<MeshRole>,
    val isGatewayActive: Boolean,
    val gatewayMode: MeshTrafficRouter.RoutingMode,
    val meshIntelligence: MeshIntelligence,
    val isTorReady: Boolean
) {
    val isActingAsGateway: Boolean get() = isGatewayActive && meshRoles.any { 
        it in setOf(MeshRole.TOR_GATEWAY, MeshRole.CLEARNET_GATEWAY, MeshRole.I2P_GATEWAY) 
    }
    
    val statusSummary: String get() = when {
        isActingAsGateway -> "Acting as ${gatewayMode.name.lowercase()} gateway"
        meshRoles.contains(MeshRole.MESH_PARTICIPANT) -> "Participating in mesh network"
        else -> "Initializing mesh connection"
    }
}
