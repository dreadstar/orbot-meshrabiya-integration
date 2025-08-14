package com.ustadmobile.meshrabiya.integration
//package com.ustadmobile.meshrabiya.integration
//
//import android.content.Context
//import android.net.ConnectivityManager
//import android.net.wifi.WifiManager
//import android.os.BatteryManager
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import com.ustadmobile.meshrabiya.beta.BetaTestLogger
//import com.ustadmobile.meshrabiya.beta.LogLevel
//import com.ustadmobile.meshrabiya.mmcp.MmcpNodeAnnouncement
//import com.ustadmobile.meshrabiya.mmcp.MmcpMessageFactory
//import com.ustadmobile.meshrabiya.vnet.MeshRoleManager
//import com.ustadmobile.meshrabiya.vnet.VirtualNode
//import com.ustadmobile.meshrabiya.vnet.NodeRole
//import io.mockk.mockk
//import org.junit.Before
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.junit.Assert.assertEquals
//import org.junit.Assert.assertTrue
//
//@RunWith(AndroidJUnit4::class)
//class MeshIntegrationTest {
//    private lateinit var context: Context
//    private lateinit var betaLogger: BetaTestLogger
//    private lateinit var roleManager: MeshRoleManager
//    private lateinit var mockVirtualNode: VirtualNode
//
//    @Before
//    fun setup() {
//        context = ApplicationProvider.getApplicationContext()
//        betaLogger = BetaTestLogger.getInstance(context)
//        mockVirtualNode = mockk(relaxed = true)
//        roleManager = MeshRoleManager(mockVirtualNode, context)
//    }
//
//    @Test
//    fun `test mesh role manager integration with new message system`() {
//        // Test that the new message system integrates with MeshRoleManager
//        val nodeAnnouncement = MmcpMessageFactory.createSimpleNodeAnnouncement(
//            messageId = 1,
//            nodeId = "test-node-1",
//            centralityScore = 0.8f
//        )
//
//        // Verify the new message structure
//        assertEquals(1, nodeAnnouncement.messageId)
//        assertEquals("test-node-1", nodeAnnouncement.nodeId)
//        assertEquals(0.9f, nodeAnnouncement.fitnessScore)
//        assertEquals(0.8f, nodeAnnouncement.centralityScore)
//
//        // Verify MeshRoleManager can work with the new system
//        assertEquals(NodeRole.MESH_NODE, roleManager.currentRole.value)
//    }
//
//    @Test
//    fun `test enhanced gossip message creation`() {
//        // Test creating different types of enhanced messages
//        val heartbeat = MmcpMessageFactory.createHeartbeat(
//            messageId = 2,
//            nodeId = "test-node-2"
//        )
//
//        assertEquals(2, heartbeat.messageId)
//        assertEquals("test-node-2", heartbeat.nodeId)
//        assertTrue(heartbeat.timestamp > 0)
//    }
//}
