package com.example.orblocalizer.network

import com.example.orblocalizer.util.Logger
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue

class SocketClient(
    private val host: String,
    private val port: Int
) {
    companion object {
        private const val TAG = "SocketClient"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_QUEUE_SIZE = 100
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val sendQueue = LinkedBlockingQueue<ByteArray>(MAX_QUEUE_SIZE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isConnected = false
    private var shouldReconnect = true

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null

    fun connect() {
        shouldReconnect = true
        scope.launch {
            connectWithRetry()
        }
        scope.launch {
            sendLoop()
        }
    }

    private suspend fun connectWithRetry() {
        while (shouldReconnect) {
            try {
                Logger.i(TAG, "Connecting to $host:$port...")
                val newSocket = Socket()
                newSocket.connect(
                    java.net.InetSocketAddress(host, port),
                    CONNECT_TIMEOUT_MS
                )
                socket = newSocket
                outputStream = newSocket.getOutputStream()
                isConnected = true
                onConnected?.invoke()
                Logger.i(TAG, "Connected to $host:$port")
                waitForDisconnect()
            } catch (e: IOException) {
                Logger.w(TAG, "Connection failed: ${e.message}")
                onError?.invoke(e)
                isConnected = false
                onDisconnected?.invoke()
                if (shouldReconnect) {
                    Logger.i(TAG, "Reconnecting in ${RECONNECT_DELAY_MS}ms...")
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    private suspend fun waitForDisconnect() {
        try {
            socket?.getInputStream()?.let { inputStream ->
                val buffer = ByteArray(1024)
                while (isConnected && shouldReconnect) {
                    val bytesRead = withContext(Dispatchers.IO) {
                        inputStream.read(buffer)
                    }
                    if (bytesRead == -1) {
                        break
                    }
                }
            }
        } catch (e: SocketException) {
            Logger.d(TAG, "Socket closed: ${e.message}")
        } finally {
            isConnected = false
            onDisconnected?.invoke()
        }
    }

    private suspend fun sendLoop() {
        while (shouldReconnect || sendQueue.isNotEmpty()) {
            try {
                val data = withContext(Dispatchers.IO) {
                    sendQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                if (data != null && isConnected) {
                    withContext(Dispatchers.IO) {
                        outputStream?.write(data)
                        outputStream?.flush()
                    }
                }
            } catch (e: IOException) {
                Logger.e(TAG, "Send error", e)
                isConnected = false
            }
        }
    }

    fun send(data: ByteArray): Boolean {
        if (!isConnected) return false
        return if (sendQueue.size < MAX_QUEUE_SIZE) {
            sendQueue.offer(data)
        } else {
            Logger.w(TAG, "Send queue full, dropping frame")
            false
        }
    }

    fun disconnect() {
        shouldReconnect = false
        isConnected = false
        try {
            socket?.close()
        } catch (e: IOException) {
            Logger.e(TAG, "Error closing socket", e)
        }
        scope.cancel()
        Logger.i(TAG, "Disconnected")
    }

    fun isConnected(): Boolean = isConnected
}
