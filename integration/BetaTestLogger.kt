import java.io.File
import kotlinx.serialization.builtins.ListSerializer

// ... inside BetaTestLogger class ...

// File names for persistent storage
private val meshEventsFile = "mesh_events.log"
private val userActionsFile = "user_actions.log"
private val networkConditionsFile = "network_conditions.log"
private val batteryImpactsFile = "battery_impacts.log"
private val installationStepsFile = "installation_steps.log"
private val protestMetricsFile = "protest_metrics.log"

init {
    // Load persisted logs on startup
    loadQueueFromDisk(meshEvents, meshEventsFile, MeshEvent.serializer())
    loadQueueFromDisk(userActions, userActionsFile, UserAction.serializer())
    loadQueueFromDisk(networkConditions, networkConditionsFile, NetworkConditions.serializer())
    loadQueueFromDisk(batteryImpacts, batteryImpactsFile, BatteryImpact.serializer())
    loadQueueFromDisk(installationSteps, installationStepsFile, InstallationStep.serializer())
    loadQueueFromDisk(protestMetrics, protestMetricsFile, ProtestMetrics.serializer())
    // Start periodic flush to disk every 5 minutes
    startPeriodicFlush()
    startAutoCleanup()
}

private fun <T> persistQueueToDisk(queue: ConcurrentLinkedQueue<T>, fileName: String, serializer: KSerializer<T>) {
    try {
        val jsonString = json.encodeToString(ListSerializer(serializer), queue.toList())
        val encrypted = secureStorage.encrypt(jsonString)
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { it.write(encrypted.toByteArray()) }
    } catch (e: Exception) {
        // Log or handle error (e.g., storage full, encryption error)
        e.printStackTrace()
    }
}

private fun <T> loadQueueFromDisk(queue: ConcurrentLinkedQueue<T>, fileName: String, serializer: KSerializer<T>) {
    val file = File(context.filesDir, fileName)
    if (file.exists()) {
        try {
            val encrypted = file.readText()
            val jsonString = secureStorage.decrypt(encrypted)
            val items = json.decodeFromString(ListSerializer(serializer), jsonString)
            queue.addAll(items)
        } catch (e: Exception) {
            // Log or handle error (e.g., corrupted file, decryption error)
            e.printStackTrace()
            file.delete() // Optionally delete corrupted file
        }
    }
}

private fun startPeriodicFlush() {
    coroutineScope.launch {
        while (true) {
            delay(5 * 60 * 1000L) // 5 minutes
            persistQueueToDisk(meshEvents, meshEventsFile, MeshEvent.serializer())
            persistQueueToDisk(userActions, userActionsFile, UserAction.serializer())
            persistQueueToDisk(networkConditions, networkConditionsFile, NetworkConditions.serializer())
            persistQueueToDisk(batteryImpacts, batteryImpactsFile, BatteryImpact.serializer())
            persistQueueToDisk(installationSteps, installationStepsFile, InstallationStep.serializer())
            persistQueueToDisk(protestMetrics, protestMetricsFile, ProtestMetrics.serializer())
        }
    }
}

// Public flush method for app lifecycle events
fun flush() {
    persistQueueToDisk(meshEvents, meshEventsFile, MeshEvent.serializer())
    persistQueueToDisk(userActions, userActionsFile, UserAction.serializer())
    persistQueueToDisk(networkConditions, networkConditionsFile, NetworkConditions.serializer())
    persistQueueToDisk(batteryImpacts, batteryImpactsFile, BatteryImpact.serializer())
    persistQueueToDisk(installationSteps, installationStepsFile, InstallationStep.serializer())
    persistQueueToDisk(protestMetrics, protestMetricsFile, ProtestMetrics.serializer())
}
