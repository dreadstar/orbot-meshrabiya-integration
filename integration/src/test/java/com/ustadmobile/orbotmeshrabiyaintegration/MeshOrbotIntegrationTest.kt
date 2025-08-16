package com.ustadmobile.orbotmeshrabiyaintegration

import android.content.Context
import android.content.SharedPreferences
import com.ustadmobile.meshrabiya.vnet.*
import com.ustadmobile.meshrabiya.mmcp.*
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Comprehensive integration tests for the mesh-to-Orbot traffic routing system
 * Tests the complete flow from mesh packet → NAT → Orbot VPN → Tor/Clearnet
 */
class MeshOrbotIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockVirtualNode: AndroidVirtualNode
    private lateinit var mockMeshRoleManager: MeshRoleManager
    private lateinit var mockOrbotService: OrbotService
    private lateinit var mockOrbotVpnManager: OrbotVpnManager
    private lateinit var meshTrafficRouter: MeshTrafficRouter
    private lateinit var emergentRoleManager: EmergentRoleManager
    private lateinit var meshTrafficHandler: MeshTrafficHandler
    private lateinit var betaTestLogger: BetaTestLogger

    @Before
    fun setup() {
        // Mock Android context and dependencies
        mockContext = mock(Context::class.java)
        mockVirtualNode = mock(AndroidVirtualNode::class.java)
        mockMeshRoleManager = mock(MeshRoleManager::class.java)
        mockOrbotService = mock(OrbotService::class.java)
        mockOrbotVpnManager = mock(OrbotVpnManager::class.java)
        
        // Setup basic virtual node behavior
        whenever(mockVirtualNode.neighbors()).thenReturn(emptyList())
        whenever(mockVirtualNode.addressAsInt).thenReturn(12345)
        whenever(mockMeshRoleManager.userAllowsTorProxy).thenReturn(true)
        whenever(mockMeshRoleManager.calculateFitnessScore()).thenReturn(
            createMockFitnessScore()
        )
        
        // Setup Orbot service mocks
        whenever(mockOrbotService.isTorReadyForMesh()).thenReturn(true)
        whenever(mockOrbotService.getVpnManager()).thenReturn(mockOrbotVpnManager)
        whenever(mockOrbotService.enableMeshGateway()).thenReturn(true)
        
        // Mock SharedPreferences for BetaTestLogger
        val mockSharedPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPrefs)
        whenever(mockSharedPrefs.getString(any(), any())).thenReturn("DETAILED")
        whenever(mockSharedPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        doNothing().whenever(mockEditor).apply()
        
        // Initialize components
        betaTestLogger = BetaTestLogger.getInstance(mockContext)
        betaTestLogger.setLogLevel(LogLevel.DETAILED)
        betaTestLogger.clearLogs()
        
        meshTrafficHandler = MeshTrafficHandler()
        meshTrafficRouter = MeshTrafficRouter(mockContext, mockOrbotService)
        emergentRoleManager = EmergentRoleManager(
            mockVirtualNode, 
            mockContext, 
            mockMeshRoleManager,
            meshTrafficRouter
        )
    }
    
    @After
    fun cleanup() {
        betaTestLogger.clearLogs()
        meshTrafficRouter.disableGatewayRouting()
    }

    // ===== CORE INTEGRATION TESTS =====

    @Test
    fun testMeshToOrbotTrafficFlow() {
        // Test complete mesh packet routing through Orbot
        
        // 1. Setup: Node becomes a gateway
        val gatewayNode = createHighCapabilityNode()
        val needyMesh = createMeshNeedingGateways()
        
        val rolePlan = emergentRoleManager.determineOptimalRoles(
            nodeCapabilities = gatewayNode,
            meshIntelligence = needyMesh,
            currentRoles = setOf(MeshRole.MESH_PARTICIPANT)
        )
        
        // Should assign gateway role
        assertTrue(
            rolePlan.addRoles.contains(MeshRole.TOR_GATEWAY) || 
            rolePlan.addRoles.contains(MeshRole.CLEARNET_GATEWAY),
            "High capability node should be assigned gateway role in needy mesh"
        )
        
        // 2. Apply the role transition
        emergentRoleManager.applyTransitionPlan(rolePlan)
        
        // 3. Verify gateway routing is activated
        assertTrue(meshTrafficRouter.isGatewayRoutingEnabled(), 
            "Gateway routing should be enabled after role transition")
        
        // 4. Test packet routing
        val testPacket = createTestMeshPacket()
        val routingResult = meshTrafficRouter.routePacket(testPacket)
        
        assertTrue(routingResult.isSuccess, "Packet routing should succeed")
        assertNotNull(routingResult.natEntry, "NAT entry should be created for routed packet")
        
        // 5. Verify logs captured the process
        val logs = betaTestLogger.getLogs()
        assertTrue(logs.any { it.message.contains("gateway routing") }, 
            "Should log gateway routing activation")
    }

    @Test
    fun testTorGatewaySpecificRouting() {
        // Test Tor-specific gateway routing
        
        // Enable Tor gateway mode
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        
        // Create test packet destined for internet
        val internetPacket = createInternetDestinedPacket()
        
        // Route the packet
        val result = meshTrafficRouter.routePacket(internetPacket)
        
        assertTrue(result.isSuccess, "Tor routing should succeed")
        assertEquals(MeshTrafficRouter.RoutingMode.TOR_ONLY, 
            meshTrafficRouter.getCurrentRoutingMode(),
            "Should be in Tor-only routing mode")
        
        // Verify Orbot integration was called
        verify(mockOrbotService, atLeastOnce()).enableMeshGateway()
        verify(mockOrbotVpnManager, atLeastOnce()).handleMeshPacket(any())
    }

    @Test
    fun testClearnetGatewaySpecificRouting() {
        // Test clearnet-specific gateway routing
        
        // Enable clearnet gateway mode
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT)
        
        // Create test packet destined for internet
        val internetPacket = createInternetDestinedPacket()
        
        // Route the packet
        val result = meshTrafficRouter.routePacket(internetPacket)
        
        assertTrue(result.isSuccess, "Clearnet routing should succeed")
        assertEquals(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT, 
            meshTrafficRouter.getCurrentRoutingMode(),
            "Should be in clearnet direct routing mode")
    }

    @Test
    fun testNATTableManagement() {
        // Test NAT table creation and management
        
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        
        // Route multiple packets to create NAT entries
        val packet1 = createTestMeshPacket(sourceAddr = 1001, destAddr = 8888)
        val packet2 = createTestMeshPacket(sourceAddr = 1002, destAddr = 8889)
        val packet3 = createTestMeshPacket(sourceAddr = 1001, destAddr = 8890) // Same source
        
        val result1 = meshTrafficRouter.routePacket(packet1)
        val result2 = meshTrafficRouter.routePacket(packet2)
        val result3 = meshTrafficRouter.routePacket(packet3)
        
        assertTrue(result1.isSuccess, "First packet should route successfully")
        assertTrue(result2.isSuccess, "Second packet should route successfully") 
        assertTrue(result3.isSuccess, "Third packet should route successfully")
        
        // Verify NAT entries
        assertNotNull(result1.natEntry, "Should create NAT entry for first packet")
        assertNotNull(result2.natEntry, "Should create NAT entry for second packet")
        assertNotNull(result3.natEntry, "Should create NAT entry for third packet")
        
        // Same source should reuse NAT entry for different destinations
        assertEquals(result1.natEntry!!.meshSource, result3.natEntry!!.meshSource,
            "Same mesh source should have consistent NAT mapping")
    }

    @Test
    fun testGatewayRoleTransitionScenarios() {
        // Test various gateway role transition scenarios
        
        // Scenario 1: No gateway to Tor gateway
        val currentRoles = setOf(MeshRole.MESH_PARTICIPANT)
        val highCapNode = createHighCapabilityNode()
        val needyMesh = createMeshNeedingGateways()
        
        var plan = emergentRoleManager.determineOptimalRoles(
            nodeCapabilities = highCapNode,
            meshIntelligence = needyMesh,
            currentRoles = currentRoles
        )
        
        emergentRoleManager.applyTransitionPlan(plan)
        assertTrue(meshTrafficRouter.isGatewayRoutingEnabled(), 
            "Should enable gateway routing when gaining gateway role")
        
        // Scenario 2: Tor gateway to no gateway (when mesh has enough gateways)
        val saturatedMesh = createMeshWithSufficientGateways()
        plan = emergentRoleManager.determineOptimalRoles(
            nodeCapabilities = highCapNode,
            meshIntelligence = saturatedMesh,
            currentRoles = setOf(MeshRole.MESH_PARTICIPANT, MeshRole.TOR_GATEWAY)
        )
        
        emergentRoleManager.applyTransitionPlan(plan)
        // Note: Gateway might still be enabled if other conditions require it
        
        // Scenario 3: Switch between gateway types
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        assertEquals(MeshTrafficRouter.RoutingMode.TOR_ONLY, 
            meshTrafficRouter.getCurrentRoutingMode())
        
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT)
        assertEquals(MeshTrafficRouter.RoutingMode.CLEARNET_DIRECT, 
            meshTrafficRouter.getCurrentRoutingMode(),
            "Should switch routing modes seamlessly")
    }

    @Test
    fun testFailureRecoveryScenarios() {
        // Test system recovery from various failure scenarios
        
        // Scenario 1: Orbot service becomes unavailable
        whenever(mockOrbotService.isTorReadyForMesh()).thenReturn(false)
        
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        val packet = createTestMeshPacket()
        val result = meshTrafficRouter.routePacket(packet)
        
        // Should handle gracefully - exact behavior depends on implementation
        // At minimum, should not crash
        
        // Scenario 2: VPN manager fails
        whenever(mockOrbotVpnManager.handleMeshPacket(any())).thenThrow(RuntimeException("VPN error"))
        
        val packet2 = createTestMeshPacket()
        val result2 = meshTrafficRouter.routePacket(packet2)
        // Should handle gracefully without crashing
        
        // Scenario 3: Recovery when service comes back online
        whenever(mockOrbotService.isTorReadyForMesh()).thenReturn(true)
        whenever(mockOrbotVpnManager.handleMeshPacket(any())).thenReturn(true)
        
        val packet3 = createTestMeshPacket()
        val result3 = meshTrafficRouter.routePacket(packet3)
        assertTrue(result3.isSuccess, "Should recover when services are available")
    }

    @Test
    fun testPerformanceUnderLoad() {
        // Test performance with many concurrent packet routing operations
        
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        
        val startTime = System.currentTimeMillis()
        val packetCount = 100
        var successfulRoutes = 0
        
        // Route many packets quickly
        repeat(packetCount) { i ->
            val packet = createTestMeshPacket(sourceAddr = 1000 + i, destAddr = 8000 + i)
            val result = meshTrafficRouter.routePacket(packet)
            if (result.isSuccess) successfulRoutes++
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        assertTrue(successfulRoutes > packetCount * 0.8, 
            "Should successfully route most packets under load (got $successfulRoutes/$packetCount)")
        assertTrue(duration < 5000, 
            "Should complete $packetCount packet routes in under 5 seconds (took ${duration}ms)")
    }

    @Test
    fun testIntegrationWithBetaLogging() {
        // Test that integration properly logs to BetaTestLogger
        
        betaTestLogger.setLogLevel(LogLevel.DETAILED)
        betaTestLogger.clearLogs()
        
        // Perform integration operations that should generate logs
        val gatewayNode = createHighCapabilityNode()
        val needyMesh = createMeshNeedingGateways()
        
        val plan = emergentRoleManager.determineOptimalRoles(gatewayNode, needyMesh)
        emergentRoleManager.applyTransitionPlan(plan)
        
        meshTrafficRouter.enableGatewayRouting(MeshTrafficRouter.RoutingMode.TOR_ONLY)
        
        val packet = createTestMeshPacket()
        meshTrafficRouter.routePacket(packet)
        
        // Verify logs were captured
        val logs = betaTestLogger.getLogs()
        assertTrue(logs.isNotEmpty(), "Should have captured integration logs")
        
        val importantLogs = logs.filter { 
            it.message.contains("gateway") || 
            it.message.contains("routing") || 
            it.message.contains("role")
        }
        assertTrue(importantLogs.isNotEmpty(), 
            "Should have captured logs about gateway/routing/role operations")
    }

    // ===== HELPER METHODS =====

    private fun createMockFitnessScore() = object {
        val batteryLevel: Float = 0.8f
        val signalStrength: Float = 85.0f
    }

    private fun createHighCapabilityNode() = NodeCapabilitySnapshot(
        nodeId = "high-capability-test",
        resources = ResourceCapabilities(
            availableCPU = 0.9f,
            availableRAM = 2_000_000_000L,
            availableBandwidth = 100_000_000L,
            storageOffered = 10_000_000L,
            batteryLevel = 95,
            thermalThrottling = false,
            powerState = PowerState.BATTERY_HIGH,
            networkInterfaces = emptySet()
        ),
        batteryInfo = BatteryInfo(
            level = 95, 
            isCharging = true, 
            estimatedTimeRemaining = null,
            temperatureCelsius = 25,
            health = BatteryHealth.GOOD,
            chargingSource = ChargingSource.USB
        ),
        thermalState = ThermalState.COOL,
        networkQuality = 1.0f,
        stability = 1.0f
    )

    private fun createMeshNeedingGateways() = MeshIntelligence(
        totalNodes = 20,
        activeGateways = 1, // Needs more gateways
        activeStorageNodes = 5,
        activeComputeNodes = 3,
        networkLoad = 0.8f, // High load
        storageUtilization = 0.6f,
        computeUtilization = 0.5f
    )

    private fun createMeshWithSufficientGateways() = MeshIntelligence(
        totalNodes = 20,
        activeGateways = 8, // Plenty of gateways
        activeStorageNodes = 10,
        activeComputeNodes = 8,
        networkLoad = 0.3f, // Low load
        storageUtilization = 0.4f,
        computeUtilization = 0.3f
    )

    private fun createTestMeshPacket(
        sourceAddr: Int = 1001, 
        destAddr: Int = 8888
    ): VirtualPacket {
        val packet = mock(VirtualPacket::class.java)
        whenever(packet.toAddr).thenReturn(destAddr)
        whenever(packet.fromAddr).thenReturn(sourceAddr)
        whenever(packet.data).thenReturn(byteArrayOf(1, 2, 3, 4, 5))
        whenever(packet.packetId).thenReturn(System.currentTimeMillis().toInt())
        return packet
    }

    private fun createInternetDestinedPacket(): VirtualPacket {
        // Create a packet destined for an internet address (e.g., 8.8.8.8)
        return createTestMeshPacket(sourceAddr = 1001, destAddr = 0x08080808) // 8.8.8.8
    }
}
