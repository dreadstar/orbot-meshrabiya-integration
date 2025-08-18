package com.ustadmobile.orbotmeshrabiyaintegration

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ustadmobile.meshrabiya.vnet.*
import com.ustadmobile.meshrabiya.mmcp.*
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import com.ustadmobile.orbotmeshrabiyaintegration.interfaces.TorService
import com.ustadmobile.orbotmeshrabiyaintegration.interfaces.MeshTrafficRouter
import com.ustadmobile.orbotmeshrabiyaintegration.routing.MeshTrafficRouterImpl
import com.ustadmobile.orbotmeshrabiyaintegration.routing.OrbotServiceImpl
import com.ustadmobile.orbotmeshrabiyaintegration.config.MeshServiceConfiguration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import java.util.concurrent.ScheduledExecutorService
import kotlin.coroutines.coroutineContext

// DataStore extension for application context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mesh_settings")

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
    private lateinit var torService: TorService
    private lateinit var serviceConfiguration: MeshServiceConfiguration
    
    // Shared services
    private lateinit var executorService: ScheduledExecutorService
    
    // Track routing state locally
    @Volatile
    private var gatewayActive = false
    private var routingMode = MeshTrafficRouter.GatewayMode.NONE
    
    private val applicationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing Mesh-Orbot Integration Application")
        
        // Initialize service configuration
        initializeServiceConfiguration()
        
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
    
    
    private fun initializeServiceConfiguration() {
        serviceConfiguration = MeshServiceConfiguration(this)
        
        // Check configuration status
        val configSummary = serviceConfiguration.generateConfigurationSummary()
        
        Log.d(TAG, "Service configuration status:")
        Log.d(TAG, "- Permissions: ${configSummary.permissionStatus.hasAllPermissions}")
        Log.d(TAG, "- VPN Service: ${configSummary.serviceStatus.isConfigured}")
        Log.d(TAG, "- Directories: ${configSummary.directoryStatus.success}")
        Log.d(TAG, "- Orbot: ${serviceConfiguration.checkOrbotAvailability().isInstalled}")
        
        if (!configSummary.isFullyConfigured) {
            Log.w(TAG, "Service configuration incomplete - some features may not work")
            Log.w(TAG, "Missing permissions: ${configSummary.permissionStatus.missingPermissions}")
        }
    }
    
    private fun initializeBetaLogging() {
        betaTestLogger = BetaTestLogger.getInstance(this)
        // Set logging level based on debug build or user preference
        val logLevel = LogLevel.BASIC  // Simplified - remove BuildConfig dependency
        betaTestLogger.setLogLevel(logLevel)
        
        betaTestLogger.log(LogLevel.INFO, TAG, "Beta logging initialized with level: $logLevel")
    }
    
    private fun initializeCoreComponents() {
        try {
            // Initialize Tor service (dependency injected in real app)
            torService = createTorServiceImpl()
            
            // Initialize mesh components  
            val dataStore = applicationContext.dataStore
            executorService = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
            
            androidVirtualNode = AndroidVirtualNode(
                context = this,
                dataStore = dataStore,
                scheduledExecutorService = executorService
            )
            meshRoleManager = MeshRoleManager(androidVirtualNode, this)
            
            // Initialize traffic router (dependency injected in real app)
            meshTrafficRouter = createMeshTrafficRouterImpl()
            
            // Initialize emergent role manager
            emergentRoleManager = EmergentRoleManager(
                virtualNode = androidVirtualNode,
                context = this,
                meshRoleManager = meshRoleManager,
                meshTrafficRouter = meshTrafficRouter
            )
            
            betaTestLogger.log(LogLevel.INFO, TAG, "Core components initialized successfully")
            
        } catch (e: Exception) {
            betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to initialize core components: ${e.message}", throwable = e)
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
            betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to setup integration: ${e.message}", throwable = e)
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
            while (gatewayActive) {
                try {
                    emergentRoleManager.updateRoles()
                    delay(30_000) // Update every 30 seconds
                } catch (e: Exception) {
                    betaTestLogger.log(LogLevel.ERROR, TAG, 
                        "Error in periodic role update: ${e.message}", throwable = e)
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
                // Ensure Tor is ready
                if (torService.isTorReadyForMesh()) {
                    meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.GatewayMode.TOR_GATEWAY)
                    routingMode = MeshTrafficRouter.GatewayMode.TOR_GATEWAY
                    gatewayActive = true
                    betaTestLogger.log(LogLevel.INFO, TAG, "Tor gateway routing enabled successfully")
                } else {
                    betaTestLogger.log(LogLevel.WARN, TAG, "Tor not ready for mesh integration")
                }
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to enable Tor gateway: ${e.message}", throwable = e)
            }
        }
    }
    
    private fun handleClearnetGatewayRoleAcquired() {
        betaTestLogger.log(LogLevel.INFO, TAG, "Acquired clearnet gateway role - enabling direct routing")
        
        applicationScope.launch(Dispatchers.IO) {
            try {
                meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.GatewayMode.CLEARNET_GATEWAY)
                routingMode = MeshTrafficRouter.GatewayMode.CLEARNET_GATEWAY
                gatewayActive = true
                betaTestLogger.log(LogLevel.INFO, TAG, "Clearnet gateway routing enabled successfully")
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to enable clearnet gateway: ${e.message}", throwable = e)
            }
        }
    }
    
    private fun handleGatewayRoleRemoved() {
        betaTestLogger.log(LogLevel.INFO, TAG, "Gateway role removed - disabling gateway routing")
        
        applicationScope.launch(Dispatchers.IO) {
            try {
                meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.GatewayMode.NONE)
                routingMode = MeshTrafficRouter.GatewayMode.NONE
                gatewayActive = false
                betaTestLogger.log(LogLevel.INFO, TAG, "Gateway routing disabled successfully")
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, "Failed to disable gateway routing: ${e.message}", throwable = e)
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
                    "Error updating roles based on mesh intelligence: ${e.message}", throwable = e)
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
                    "Error monitoring network connectivity: ${e.message}", throwable = e)
                delay(120_000) // Back off on error
            }
        }
    }
    
    private suspend fun monitorOrbotServiceAvailability() {
        while (coroutineContext.isActive) {
            try {
                val isTorReady = torService.isTorReadyForMesh()
                val currentRoles = emergentRoleManager.getCurrentMeshRoles()
                
                if (!isTorReady && MeshRole.TOR_GATEWAY in currentRoles) {
                    betaTestLogger.log(LogLevel.WARN, TAG, 
                        "Tor service not ready - may need to switch gateway mode")
                    
                    // Try switching to clearnet gateway mode
                    if (checkInternetConnectivity()) {
                        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.GatewayMode.CLEARNET_GATEWAY)
                        routingMode = MeshTrafficRouter.GatewayMode.CLEARNET_GATEWAY
                        betaTestLogger.log(LogLevel.INFO, TAG, "Switched to clearnet gateway mode")
                    }
                }
                
                delay(30_000) // Check every 30 seconds
                
            } catch (e: Exception) {
                betaTestLogger.log(LogLevel.ERROR, TAG, 
                    "Error monitoring Tor service: ${e.message}", throwable = e)
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
                betaTestLogger.log(LogLevel.ERROR, TAG, "Manual role update failed: ${e.message}", throwable = e)
            }
        }
    }
    
    /**
     * Public API for getting current status
     */
    fun getCurrentStatus(): IntegrationStatus {
        return IntegrationStatus(
            meshRoles = emergentRoleManager.getCurrentMeshRoles(),
            isGatewayActive = meshTrafficRouter.isGatewayActive(),
            gatewayMode = meshTrafficRouter.getCurrentGatewayMode().name,
            meshIntelligence = emergentRoleManager.getMeshIntelligence(),
            isTorReady = torService.isTorReadyForMesh()
        )
    }
    
    // Factory methods for dependency injection
    private fun createTorServiceImpl(): TorService {
        // Create real Orbot service implementation
        return OrbotServiceImpl(this)
    }
    
    private fun createMeshTrafficRouterImpl(): MeshTrafficRouter {
        // Create real mesh traffic router implementation
        return MeshTrafficRouterImpl(this)
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        betaTestLogger.log(LogLevel.INFO, TAG, "Application terminating - cleaning up")
        
        // Cleanup integration
        applicationScope.launch {
            try {
                meshTrafficRouter.cleanup()
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
    val gatewayMode: String, // Simplified to String for now
    val meshIntelligence: MeshIntelligence,
    val isTorReady: Boolean
) {
    val isActingAsGateway: Boolean get() = isGatewayActive && meshRoles.any { 
        it in setOf(MeshRole.TOR_GATEWAY, MeshRole.CLEARNET_GATEWAY, MeshRole.I2P_GATEWAY) 
    }
    
    val statusSummary: String get() = when {
        isActingAsGateway -> "Acting as ${gatewayMode.lowercase()} gateway"
        meshRoles.contains(MeshRole.MESH_PARTICIPANT) -> "Participating in mesh network"
        else -> "Initializing mesh connection"
    }
}
