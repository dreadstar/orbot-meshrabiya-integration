package com.ustadmobile.meshrabiya.integration

import com.ustadmobile.meshrabiya.beta.BetaTestLogger
import com.ustadmobile.meshrabiya.beta.LogLevel
import com.ustadmobile.meshrabiya.beta.LogEntry
import org.junit.Test
import org.junit.Assert.*

/**
 * Validation test to verify that BetaTestLogger implements the exact requirements:
 * - When BetaConsentActivity LogLevel is:
 *   * DISABLED: BetaTestLogger should log only ERROR logs
 *   * BASIC: BetaTestLogger should log WARN, ERROR logs  
 *   * DETAILED: BetaTestLogger should log WARN, ERROR, INFO logs
 *   * FULL: BetaTestLogger should log DEBUG, INFO, WARN, ERROR logs
 */
class BetaLoggerValidationTest {

    @Test
    fun `verify DISABLED consent level logs only ERROR`() {
        val logger = BetaTestLogger.createTestInstance()
        logger.setLogLevel(LogLevel.DISABLED)
        
        // Test all log levels
        logger.log(LogLevel.DEBUG, "Test", "Debug message")
        logger.log(LogLevel.INFO, "Test", "Info message") 
        logger.log(LogLevel.WARN, "Test", "Warn message")
        logger.log(LogLevel.ERROR, "Test", "Error message")
        
        val logs = logger.getLogs()
        
        // Should only have ERROR log
        assertEquals("DISABLED should only log ERROR", 1, logs.size)
        assertEquals("Should be ERROR level", LogLevel.ERROR, logs[0].level)
        assertTrue("Should contain error message", logs[0].message.contains("Error message"))
    }

    @Test
    fun `verify BASIC consent level logs WARN and ERROR`() {
        val logger = BetaTestLogger.createTestInstance()
        logger.setLogLevel(LogLevel.BASIC)
        
        // Test all log levels
        logger.log(LogLevel.DEBUG, "Test", "Debug message")
        logger.log(LogLevel.INFO, "Test", "Info message")
        logger.log(LogLevel.WARN, "Test", "Warn message") 
        logger.log(LogLevel.ERROR, "Test", "Error message")
        
        val logs = logger.getLogs()
        
        // Should have WARN and ERROR logs
        assertEquals("BASIC should log WARN and ERROR", 2, logs.size)
        
        val logLevels = logs.map { it.level }.toSet()
        assertTrue("Should contain WARN", logLevels.contains(LogLevel.WARN))
        assertTrue("Should contain ERROR", logLevels.contains(LogLevel.ERROR))
        assertFalse("Should not contain DEBUG", logLevels.contains(LogLevel.DEBUG))
        assertFalse("Should not contain INFO", logLevels.contains(LogLevel.INFO))
    }

    @Test
    fun `verify DETAILED consent level logs WARN ERROR INFO`() {
        val logger = BetaTestLogger.createTestInstance()
        logger.setLogLevel(LogLevel.DETAILED)
        
        // Test all log levels
        logger.log(LogLevel.DEBUG, "Test", "Debug message")
        logger.log(LogLevel.INFO, "Test", "Info message")
        logger.log(LogLevel.WARN, "Test", "Warn message")
        logger.log(LogLevel.ERROR, "Test", "Error message")
        
        val logs = logger.getLogs()
        
        // Should have INFO, WARN and ERROR logs
        assertEquals("DETAILED should log INFO, WARN and ERROR", 3, logs.size)
        
        val logLevels = logs.map { it.level }.toSet()
        assertTrue("Should contain INFO", logLevels.contains(LogLevel.INFO))
        assertTrue("Should contain WARN", logLevels.contains(LogLevel.WARN))
        assertTrue("Should contain ERROR", logLevels.contains(LogLevel.ERROR))
        assertFalse("Should not contain DEBUG", logLevels.contains(LogLevel.DEBUG))
    }

    @Test
    fun `verify FULL consent level logs all levels`() {
        val logger = BetaTestLogger.createTestInstance()
        logger.setLogLevel(LogLevel.FULL)
        
        // Test all log levels
        logger.log(LogLevel.DEBUG, "Test", "Debug message")
        logger.log(LogLevel.INFO, "Test", "Info message")
        logger.log(LogLevel.WARN, "Test", "Warn message")
        logger.log(LogLevel.ERROR, "Test", "Error message")
        
        val logs = logger.getLogs()
        
        // Should have all log levels
        assertEquals("FULL should log all levels", 4, logs.size)
        
        val logLevels = logs.map { it.level }.toSet()
        assertTrue("Should contain DEBUG", logLevels.contains(LogLevel.DEBUG))
        assertTrue("Should contain INFO", logLevels.contains(LogLevel.INFO))
        assertTrue("Should contain WARN", logLevels.contains(LogLevel.WARN))
        assertTrue("Should contain ERROR", logLevels.contains(LogLevel.ERROR))
    }
}
