package com.ustadmobile.orbotmeshrabiyaintegration.routing

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer

/**
 * SOCKS5 proxy client for routing mesh traffic through Tor
 * Handles the SOCKS5 protocol negotiation and data forwarding
 */
class SocksProxyClient(
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int
) {
    
    companion object {
        private const val TAG = "SocksProxyClient"
        private const val SOCKS_VERSION = 0x05
        private const val CONNECT_COMMAND = 0x01
        private const val ATYP_IPV4 = 0x01
        private const val ATYP_DOMAIN = 0x03
        private const val AUTH_NONE = 0x00
        private const val RESPONSE_SUCCESS = 0x00
        private const val CONNECTION_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 30000 // 30 seconds
    }
    
    /**
     * Create a SOCKS5 connection to the specified target
     */
    suspend fun createConnection(targetHost: String, targetPort: Int): Socket? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating SOCKS5 connection to $targetHost:$targetPort via $socksHost:$socksPort")
                
                val socket = Socket()
                socket.soTimeout = READ_TIMEOUT
                socket.connect(InetSocketAddress(socksHost, socksPort), CONNECTION_TIMEOUT)
                
                // Perform SOCKS5 handshake
                if (performSocks5Handshake(socket, targetHost, targetPort)) {
                    Log.d(TAG, "SOCKS5 connection established successfully")
                    socket
                } else {
                    Log.e(TAG, "SOCKS5 handshake failed")
                    socket.close()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SOCKS5 connection", e)
                null
            }
        }
    }
    
    private suspend fun performSocks5Handshake(socket: Socket, targetHost: String, targetPort: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()
                
                // Step 1: Send authentication methods
                if (!sendAuthenticationMethods(outputStream)) {
                    return@withContext false
                }
                
                // Step 2: Receive authentication method selection
                if (!receiveAuthenticationSelection(inputStream)) {
                    return@withContext false
                }
                
                // Step 3: Send connection request
                if (!sendConnectionRequest(outputStream, targetHost, targetPort)) {
                    return@withContext false
                }
                
                // Step 4: Receive connection response
                receiveConnectionResponse(inputStream)
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS5 handshake error", e)
                false
            }
        }
    }
    
    private fun sendAuthenticationMethods(outputStream: OutputStream): Boolean {
        return try {
            // SOCKS5 authentication method negotiation
            // Format: [Version][Number of methods][Method 1][Method 2]...
            val request = byteArrayOf(
                SOCKS_VERSION.toByte(),  // SOCKS version 5
                0x01,                    // Number of authentication methods
                AUTH_NONE.toByte()       // No authentication required
            )
            
            outputStream.write(request)
            outputStream.flush()
            
            Log.d(TAG, "Sent SOCKS5 authentication methods")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send authentication methods", e)
            false
        }
    }
    
    private fun receiveAuthenticationSelection(inputStream: InputStream): Boolean {
        return try {
            val response = ByteArray(2)
            inputStream.read(response)
            
            val version = response[0].toInt() and 0xFF
            val selectedMethod = response[1].toInt() and 0xFF
            
            if (version != SOCKS_VERSION) {
                Log.e(TAG, "Invalid SOCKS version in response: $version")
                return false
            }
            
            if (selectedMethod != AUTH_NONE) {
                Log.e(TAG, "Authentication method not supported: $selectedMethod")
                return false
            }
            
            Log.d(TAG, "Received SOCKS5 authentication selection: no auth")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive authentication selection", e)
            false
        }
    }
    
    private fun sendConnectionRequest(outputStream: OutputStream, targetHost: String, targetPort: Int): Boolean {
        return try {
            // SOCKS5 connection request
            // Format: [Version][Command][Reserved][Address Type][Address][Port]
            
            val request = ByteArrayOutputStream()
            
            // Fixed header
            request.write(SOCKS_VERSION)  // Version
            request.write(CONNECT_COMMAND) // Command (CONNECT)
            request.write(0x00)           // Reserved
            
            // Address
            if (isValidIpAddress(targetHost)) {
                // IPv4 address
                request.write(ATYP_IPV4)
                val address = InetAddress.getByName(targetHost)
                request.write(address.address)
            } else {
                // Domain name
                request.write(ATYP_DOMAIN)
                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                request.write(hostBytes.size) // Domain name length
                request.write(hostBytes)      // Domain name
            }
            
            // Port (2 bytes, big-endian)
            request.write((targetPort shr 8) and 0xFF)
            request.write(targetPort and 0xFF)
            
            outputStream.write(request.toByteArray())
            outputStream.flush()
            
            Log.d(TAG, "Sent SOCKS5 connection request for $targetHost:$targetPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send connection request", e)
            false
        }
    }
    
    private fun receiveConnectionResponse(inputStream: InputStream): Boolean {
        return try {
            val response = ByteArray(10) // Maximum response size for IPv4
            val bytesRead = inputStream.read(response, 0, 4) // Read fixed part first
            
            if (bytesRead < 4) {
                Log.e(TAG, "Incomplete SOCKS5 response")
                return false
            }
            
            val version = response[0].toInt() and 0xFF
            val replyCode = response[1].toInt() and 0xFF
            val addressType = response[3].toInt() and 0xFF
            
            if (version != SOCKS_VERSION) {
                Log.e(TAG, "Invalid SOCKS version in connection response: $version")
                return false
            }
            
            if (replyCode != RESPONSE_SUCCESS) {
                Log.e(TAG, "SOCKS5 connection failed with reply code: $replyCode")
                return false
            }
            
            // Read the remaining address and port fields
            val remainingBytes = when (addressType) {
                ATYP_IPV4 -> 6    // 4 bytes IP + 2 bytes port
                ATYP_DOMAIN -> {
                    val domainLength = inputStream.read()
                    domainLength + 2  // domain + 2 bytes port
                }
                else -> {
                    Log.e(TAG, "Unsupported address type: $addressType")
                    return false
                }
            }
            
            val addressData = ByteArray(remainingBytes)
            inputStream.read(addressData)
            
            Log.d(TAG, "SOCKS5 connection established successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive connection response", e)
            false
        }
    }
    
    private fun isValidIpAddress(host: String): Boolean {
        return try {
            val parts = host.split(".")
            if (parts.size != 4) return false
            
            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Forward data between two sockets (bidirectional)
     */
    suspend fun forwardData(localSocket: Socket, remoteSocket: Socket) {
        coroutineScope {
            val job1 = launch {
                forwardStream(localSocket.getInputStream(), remoteSocket.getOutputStream(), "local->remote")
            }
            
            val job2 = launch {
                forwardStream(remoteSocket.getInputStream(), localSocket.getOutputStream(), "remote->local")
            }
            
            // Wait for either direction to complete/fail
            try {
                job1.join()
                job2.join()
            } catch (e: Exception) {
                Log.d(TAG, "Data forwarding completed", e)
            } finally {
                job1.cancel()
                job2.cancel()
            }
        }
    }
    
    private suspend fun forwardStream(inputStream: InputStream, outputStream: OutputStream, direction: String) {
        withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(8192)
                var bytesTransferred = 0L
                
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    
                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush()
                    
                    bytesTransferred += bytesRead
                    
                    // Yield occasionally to prevent blocking
                    if (bytesTransferred % 32768 == 0L) {
                        yield()
                    }
                }
                
                Log.d(TAG, "Stream forwarding completed ($direction): $bytesTransferred bytes")
            } catch (e: Exception) {
                Log.d(TAG, "Stream forwarding ended ($direction)", e)
            }
        }
    }
    
    /**
     * Send HTTP request through SOCKS proxy
     */
    suspend fun sendHttpRequest(
        targetHost: String,
        targetPort: Int,
        request: ByteArray
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val socket = createConnection(targetHost, targetPort)
            if (socket != null) {
                try {
                    // Send HTTP request
                    socket.getOutputStream().write(request)
                    socket.getOutputStream().flush()
                    
                    // Read response
                    val response = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    
                    socket.soTimeout = 5000 // 5 second timeout for reading response
                    
                    while (socket.getInputStream().read(buffer).also { bytesRead = it } != -1) {
                        response.write(buffer, 0, bytesRead)
                        
                        // Check if we have a complete HTTP response
                        val responseStr = response.toString("UTF-8")
                        if (responseStr.contains("\r\n\r\n")) {
                            // We have headers, check for content-length or chunked encoding
                            break
                        }
                    }
                    
                    socket.close()
                    response.toByteArray()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending HTTP request", e)
                    socket.close()
                    null
                }
            } else {
                null
            }
        }
    }
}
