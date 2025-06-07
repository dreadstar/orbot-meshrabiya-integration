package com.ustadmobile.meshrabiya.beta

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BetaTestLoggerTest {
    private lateinit var context: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockMasterKey: MasterKey
    private lateinit var logger: BetaTestLogger

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockSharedPrefs = mockk(relaxed = true)
        mockMasterKey = mockk(relaxed = true)

        // Mock MasterKey.Builder
        val mockBuilder = mockk<MasterKey.Builder>()
        every { mockBuilder.setKeyScheme(MasterKey.KeyScheme.AES256_GCM) } returns mockBuilder
        every { mockBuilder.setKeyGenParameterSpec(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockMasterKey

        // Mock EncryptedSharedPreferences
        every { 
            EncryptedSharedPreferences.create(
                any(),
                any(),
                mockMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } returns mockSharedPrefs

        // Mock file operations
        val mockFile = mockk<File>()
        every { context.getFilesDir() } returns mockFile
        every { mockFile.listFiles() } returns emptyArray()
        every { mockFile.delete() } returns true

        logger = BetaTestLogger.getInstance(context)
    }

    @Test
    fun `test singleton instance`() {
        val instance1 = BetaTestLogger.getInstance(context)
        val instance2 = BetaTestLogger.getInstance(context)
        assertEquals(instance1, instance2)
    }

    @Test
    fun `test log level changes`() {
        // Test default level
        assertEquals(LogLevel.BASIC, logger.getLogLevel())

        // Test level changes
        logger.setLogLevel(LogLevel.DETAILED)
        assertEquals(LogLevel.DETAILED, logger.getLogLevel())

        logger.setLogLevel(LogLevel.FULL)
        assertEquals(LogLevel.FULL, logger.getLogLevel())
    }

    @Test
    fun `test log entry creation and retrieval`() {
        val timestamp = Instant.now()
        val entry = LogEntry(
            timestamp = timestamp,
            level = LogLevel.DETAILED,
            category = "TEST",
            message = "Test message",
            metadata = mapOf("key" to "value")
        )

        logger.log(entry)

        // Verify log was added
        val logs = logger.getLogs()
        assertTrue(logs.isNotEmpty())
        assertEquals(entry, logs.first())
    }

    @Test
    fun `test log filtering by level`() {
        // Add logs of different levels
        logger.log(LogEntry(Instant.now(), LogLevel.BASIC, "TEST", "Basic log"))
        logger.log(LogEntry(Instant.now(), LogLevel.DETAILED, "TEST", "Detailed log"))
        logger.log(LogEntry(Instant.now(), LogLevel.FULL, "TEST", "Full log"))

        // Test filtering
        logger.setLogLevel(LogLevel.BASIC)
        var logs = logger.getLogs()
        assertEquals(1, logs.size)

        logger.setLogLevel(LogLevel.DETAILED)
        logs = logger.getLogs()
        assertEquals(2, logs.size)

        logger.setLogLevel(LogLevel.FULL)
        logs = logger.getLogs()
        assertEquals(3, logs.size)
    }

    @Test
    fun `test log expiration`() {
        val oldTimestamp = Instant.now().minus(31, ChronoUnit.DAYS)
        val newTimestamp = Instant.now()

        // Add old and new logs
        logger.log(LogEntry(oldTimestamp, LogLevel.BASIC, "TEST", "Old log"))
        logger.log(LogEntry(newTimestamp, LogLevel.BASIC, "TEST", "New log"))

        // Verify only new log remains
        val logs = logger.getLogs()
        assertEquals(1, logs.size)
        assertEquals("New log", logs.first().message)
    }

    @Test
    fun `test log export`() {
        // Add some test logs
        logger.log(LogEntry(Instant.now(), LogLevel.BASIC, "TEST", "Test log 1"))
        logger.log(LogEntry(Instant.now(), LogLevel.DETAILED, "TEST", "Test log 2"))

        // Export logs
        val exportFile = logger.exportLogs()
        assertNotNull(exportFile)
        assertTrue(exportFile.exists())
        assertTrue(exportFile.length() > 0)
    }

    @Test
    fun `test log clearing`() {
        // Add some logs
        logger.log(LogEntry(Instant.now(), LogLevel.BASIC, "TEST", "Test log"))
        
        // Clear logs
        logger.clearLogs()
        
        // Verify logs are cleared
        val logs = logger.getLogs()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `test log persistence`() {
        val entry = LogEntry(
            timestamp = Instant.now(),
            level = LogLevel.DETAILED,
            category = "TEST",
            message = "Persistent log",
            metadata = mapOf("key" to "value")
        )

        // Add log and flush
        logger.log(entry)
        logger.flush()

        // Create new instance to test persistence
        val newLogger = BetaTestLogger.getInstance(context)
        val logs = newLogger.getLogs()
        
        assertTrue(logs.isNotEmpty())
        assertEquals(entry.message, logs.first().message)
    }
} 