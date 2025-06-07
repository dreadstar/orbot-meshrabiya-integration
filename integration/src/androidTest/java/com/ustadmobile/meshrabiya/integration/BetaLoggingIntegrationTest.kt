package com.ustadmobile.meshrabiya.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogEntry
import com.ustadmobile.meshrabiya.beta.LogLevel
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.vnet.MeshRoleManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BetaLoggingIntegrationTest {
    private lateinit var context: Context
    private lateinit var betaLogger: BetaTestLogger
    private lateinit var roleManager: MeshRoleManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        betaLogger = BetaTestLogger.getInstance(context)
        roleManager = MeshRoleManager(context, betaLogger)
        
        // Clear any existing logs
        betaLogger.clearLogs()
    }

    @Test
    fun `test logging integration with mesh role changes`() {
        // Set log level to DETAILED
        betaLogger.setLogLevel(LogLevel.DETAILED)

        // Create and process originator message
        val message = MmcpOriginatorMessage().apply {
            neighborCount = 5
            centrality = 0.8f
            hasInternetConnectivity = true
        }

        roleManager.processOriginatorMessage(message)

        // Verify logs were created
        val logs = betaLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.any { 
            it.level == LogLevel.DETAILED &&
            it.category == "MESH_ROLE" &&
            it.message.contains("Role changed")
        })
    }

    @Test
    fun `test log level filtering integration`() {
        // Set log level to BASIC
        betaLogger.setLogLevel(LogLevel.BASIC)

        // Create and process originator message
        val message = MmcpOriginatorMessage().apply {
            neighborCount = 5
            centrality = 0.8f
            hasInternetConnectivity = true
        }

        roleManager.processOriginatorMessage(message)

        // Verify only BASIC logs are present
        val logs = betaLogger.getLogs()
        assertTrue(logs.all { it.level == LogLevel.BASIC })
    }

    @Test
    fun `test log export and import integration`() {
        // Add some test logs
        betaLogger.log(LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.DETAILED,
            category = "TEST",
            message = "Test log 1"
        ))
        betaLogger.log(LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.DETAILED,
            category = "TEST",
            message = "Test log 2"
        ))

        // Export logs
        val exportFile = betaLogger.exportLogs()
        assertNotNull(exportFile)
        assertTrue(exportFile.exists())
        assertTrue(exportFile.length() > 0)

        // Clear logs
        betaLogger.clearLogs()
        assertTrue(betaLogger.getLogs().isEmpty())

        // Import logs
        val importedLogs = betaLogger.importLogs(exportFile)
        assertTrue(importedLogs.isNotEmpty())
        assertEquals(2, importedLogs.size)
    }

    @Test
    fun `test log expiration integration`() {
        // Add old and new logs
        betaLogger.log(LogEntry(
            timestamp = Instant.now().minus(31, ChronoUnit.DAYS),
            level = LogLevel.BASIC,
            category = "TEST",
            message = "Old log"
        ))
        betaLogger.log(LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.BASIC,
            category = "TEST",
            message = "New log"
        ))

        // Verify only new log remains
        val logs = betaLogger.getLogs()
        assertEquals(1, logs.size)
        assertEquals("New log", logs.first().message)
    }

    @Test
    fun `test log persistence across instances`() {
        // Add test log
        val testMessage = "Persistent test log"
        betaLogger.log(LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.DETAILED,
            category = "TEST",
            message = testMessage
        ))

        // Create new instance
        val newLogger = BetaTestLogger.getInstance(context)
        
        // Verify log is present in new instance
        val logs = newLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertEquals(testMessage, logs.first().message)
    }

    @Test
    fun `test log metadata integration`() {
        // Add log with metadata
        val metadata = mapOf(
            "neighborCount" to "5",
            "centrality" to "0.8",
            "hasInternet" to "true"
        )
        
        betaLogger.log(LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.DETAILED,
            category = "TEST",
            message = "Test log with metadata",
            metadata = metadata
        ))

        // Verify metadata is preserved
        val logs = betaLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertEquals(metadata, logs.first().metadata)
    }

    @Test
    fun `test log rotation integration`() {
        // Add logs for different months
        val currentMonth = Instant.now()
        val lastMonth = currentMonth.minus(31, ChronoUnit.DAYS)
        
        betaLogger.log(LogEntry(
            timestamp = lastMonth,
            level = LogLevel.BASIC,
            category = "TEST",
            message = "Last month log"
        ))
        betaLogger.log(LogEntry(
            timestamp = currentMonth,
            level = LogLevel.BASIC,
            category = "TEST",
            message = "Current month log"
        ))

        // Verify only current month logs are present
        val logs = betaLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.all { it.timestamp.isAfter(lastMonth) })
    }
} 