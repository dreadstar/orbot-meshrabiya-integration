package com.ustadmobile.orbotmeshrabiyaintegration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

val MainActivity.dataStore by preferencesDataStore(name = "mesh_settings")

class MainActivity : AppCompatActivity() {
    
    private var virtualNode: AndroidVirtualNode? = null
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    
    private val scheduledExecutor = Executors.newScheduledThreadPool(4)
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        startButton.setOnClickListener { startMeshNetwork() }
        stopButton.setOnClickListener { stopMeshNetwork() }
        
        updateUI()
        
        // Check and request permissions
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, 1)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                updateUI()
            } else {
                Toast.makeText(this, "Permissions required for mesh networking", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startMeshNetwork() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                if (virtualNode == null) {
                    virtualNode = AndroidVirtualNode(
                        context = this@MainActivity,
                        dataStore = dataStore,
                        scheduledExecutor = scheduledExecutor
                    )
                }
                
                virtualNode?.setWifiHotspotEnabled(
                    enabled = true,
                    preferredBand = ConnectBand.BAND_5GHZ,
                    hotspotType = HotspotType.LOCALONLY_HOTSPOT
                )
                
                statusText.text = "Mesh network started"
                updateUI()
            } catch (e: Exception) {
                statusText.text = "Failed to start: ${e.message}"
            }
        }
    }
    
    private fun stopMeshNetwork() {
        lifecycleScope.launch {
            try {
                virtualNode?.setWifiHotspotEnabled(enabled = false, ConnectBand.BAND_5GHZ, HotspotType.LOCALONLY_HOTSPOT)
                virtualNode?.close()
                virtualNode = null
                
                statusText.text = "Mesh network stopped"
                updateUI()
            } catch (e: Exception) {
                statusText.text = "Failed to stop: ${e.message}"
            }
        }
    }
    
    private fun updateUI() {
        val isRunning = virtualNode != null
        startButton.isEnabled = !isRunning && hasRequiredPermissions()
        stopButton.isEnabled = isRunning
        
        if (statusText.text.isEmpty()) {
            statusText.text = when {
                !hasRequiredPermissions() -> "Permissions required for mesh networking"
                isRunning -> "Mesh network running"
                else -> "Mesh network stopped"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        virtualNode?.close()
        scheduledExecutor.shutdown()
    }
}