package com.example.orblocalizer.network

import com.example.orblocalizer.util.Logger

class NetworkManager(
    private val serverIp: String,
    private val serverPort: Int
) {
    companion object {
        private const val TAG = "NetworkManager"
    }

    private val socketClient = SocketClient(serverIp, serverPort)

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun connect() {
        socketClient.onConnected = {
            Logger.i(TAG, "Network connected")
            onConnected?.invoke()
        }
        socketClient.onDisconnected = {
            Logger.i(TAG, "Network disconnected")
            onDisconnected?.invoke()
        }
        socketClient.onError = { e ->
            Logger.e(TAG, "Network error", e)
        }
        socketClient.connect()
    }

    fun sendData(data: ByteArray): Boolean {
        return socketClient.send(data)
    }

    fun disconnect() {
        socketClient.disconnect()
    }

    fun isConnected(): Boolean = socketClient.isConnected()
}
