package com.connorhansen.publiclandmap.data.local.tiles

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTileServer @Inject constructor(
    @ApplicationContext context: Context,
    private val offlineTileProvider: OfflineTileProvider
) {
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private val running = AtomicBoolean(false)

    private var _port: Int = 0
    val localPort: Int get() = _port
    val baseUrl: String get() = "http://127.0.0.1:$_port"
    val isRunning: Boolean get() = running.get()

    fun startServer() {
        if (running.get()) return
        try {
            val socket = ServerSocket(0) // OS assigns free port
            serverSocket = socket
            _port = socket.localPort
            running.set(true)

            val exec = Executors.newCachedThreadPool()
            executor = exec

            exec.submit {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        exec.submit { handleClient(client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
        } catch (_: Exception) {
            running.set(false)
        }
    }

    fun stopServer() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        executor?.shutdownNow()
        serverSocket = null
        executor = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return

            // Parse: GET /tiles/z/x/y.pbf HTTP/1.1
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendNotFound(client)
                return
            }

            val path = parts[1]
            val pathParts = path.trimStart('/').split("/")

            if (pathParts.size < 4 || pathParts[0] != "tiles") {
                sendNotFound(client)
                return
            }

            val z = pathParts[1].toIntOrNull()
            val x = pathParts[2].toIntOrNull()
            val yStr = pathParts[3].removeSuffix(".pbf").removeSuffix(".mvt")
            val y = yStr.toIntOrNull()

            if (z == null || x == null || y == null) {
                sendNotFound(client)
                return
            }

            val tileData = offlineTileProvider.getTile(z, x, y)
            if (tileData != null) {
                sendTileResponse(client, tileData)
            } else {
                sendNotFound(client)
            }
        } catch (_: Exception) {
            // Client disconnected or error
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun sendTileResponse(client: Socket, data: ByteArray) {
        val output = client.getOutputStream()
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/x-protobuf\r\n")
            append("Content-Length: ${data.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(data)
        output.flush()
    }

    private fun sendNotFound(client: Socket) {
        val output = client.getOutputStream()
        val body = "Not Found"
        val header = buildString {
            append("HTTP/1.1 404 Not Found\r\n")
            append("Content-Type: text/plain\r\n")
            append("Content-Length: ${body.length}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(body.toByteArray(Charsets.US_ASCII))
        output.flush()
    }
}
