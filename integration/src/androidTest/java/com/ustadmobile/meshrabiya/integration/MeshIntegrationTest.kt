package com.ustadmobile.meshrabiya.integration

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.vnet.MeshRole
import com.ustadmobile.meshrabiya.vnet.MeshRoleManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MeshIntegrationTest {
    private lateinit var context: Context
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var betaLogger: BetaTestLogger
    private lateinit var roleManager: MeshRoleManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        betaLogger = BetaTestLogger.getInstance(context)
        roleManager = MeshRoleManager(context, betaLogger)
    }

    @Test
    fun `test mesh role changes with real system metrics`() {
        // Initial state
        assertEquals(MeshRole.CLIENT, roleManager.getCurrentRole())

        // Create and process originator message
        val message = MmcpOriginatorMessage().apply {
            neighborCount = 5
            centrality = 0.8f
            signalStrength = wifiManager.connectionInfo.rssi
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            hasInternetConnectivity = connectivityManager.activeNetwork != null
        }

        roleManager.processOriginatorMessage(message)

        // Verify role change and logging
        val logs = betaLogger.getLogs()
        assertTrue(logs.any { 
            it.level == LogLevel.DETAILED &&
            it.category == "MESH_ROLE" &&
            it.message.contains("Role changed")
        })
    }

    @Test
    fun `test mesh info propagation and role adaptation`() {
        // Create multiple nodes with different metrics
        val node1 = createNode(neighborCount = 5, centrality = 0.8f, hasInternet = true)
        val node2 = createNode(neighborCount = 3, centrality = 0.6f, hasInternet = false)
        val node3 = createNode(neighborCount = 1, centrality = 0.2f, hasInternet = false)

        // Process messages in sequence
        roleManager.processOriginatorMessage(node1)
        assertEquals(MeshRole.HOTSPOT, roleManager.getCurrentRole())

        roleManager.processOriginatorMessage(node2)
        assertEquals(MeshRole.RELAY, roleManager.getCurrentRole())

        roleManager.processOriginatorMessage(node3)
        assertEquals(MeshRole.CLIENT, roleManager.getCurrentRole())

        // Verify logging of role changes
        val logs = betaLogger.getLogs()
        assertTrue(logs.any { it.message.contains("HOTSPOT") })
        assertTrue(logs.any { it.message.contains("RELAY") })
        assertTrue(logs.any { it.message.contains("CLIENT") })
    }

    @Test
    fun `test mesh metrics persistence and recovery`() {
        // Set up initial state
        val message = createNode(neighborCount = 5, centrality = 0.8f, hasInternet = true)
        roleManager.processOriginatorMessage(message)
        assertEquals(MeshRole.HOTSPOT, roleManager.getCurrentRole())

        // Create new instances to test persistence
        val newLogger = BetaTestLogger.getInstance(context)
        val newRoleManager = MeshRoleManager(context, newLogger)

        // Verify state is maintained
        assertEquals(MeshRole.HOTSPOT, newRoleManager.getCurrentRole())
        
        val logs = newLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.any { it.message.contains("HOTSPOT") })
    }

    @Test
    fun `test mesh role adaptation under changing conditions`() {
        // Start with good conditions
        val goodMessage = createNode(neighborCount = 5, centrality = 0.8f, hasInternet = true)
        roleManager.processOriginatorMessage(goodMessage)
        assertEquals(MeshRole.HOTSPOT, roleManager.getCurrentRole())

        // Simulate degrading conditions
        val degradingMessage = createNode(neighborCount = 3, centrality = 0.6f, hasInternet = true)
        roleManager.processOriginatorMessage(degradingMessage)
        assertEquals(MeshRole.RELAY, roleManager.getCurrentRole())

        // Simulate poor conditions
        val poorMessage = createNode(neighborCount = 1, centrality = 0.2f, hasInternet = false)
        roleManager.processOriginatorMessage(poorMessage)
        assertEquals(MeshRole.CLIENT, roleManager.getCurrentRole())

        // Verify logging of role changes
        val logs = betaLogger.getLogs()
        assertTrue(logs.any { it.message.contains("HOTSPOT") })
        assertTrue(logs.any { it.message.contains("RELAY") })
        assertTrue(logs.any { it.message.contains("CLIENT") })
    }

    private fun createNode(
        neighborCount: Int,
        centrality: Float,
        hasInternet: Boolean
    ): MmcpOriginatorMessage {
        return MmcpOriginatorMessage().apply {
            this.neighborCount = neighborCount
            this.centrality = centrality
            this.signalStrength = wifiManager.connectionInfo.rssi
            this.batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            this.hasInternetConnectivity = hasInternet
        }
    }
} 