package com.kodraliu.localrock.shared.mqtt

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PahoTransportSniTest {

    /**
     * Issue #18: a reverse proxy in front of the MQTT port picks its certificate from the server
     * name in the TLS ClientHello, so the transport has to send one. A plain socket stands in for
     * the server and keeps whatever the client says first.
     */
    @Test
    fun tls_connect_names_the_server_in_the_client_hello() {
        val host = "127.0.0.1"
        val hello = CompletableFuture<ByteArray>()
        ServerSocket(0, 1, InetAddress.getByName(host)).use { server ->
            thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        val buffer = ByteArray(4096)
                        val read = socket.getInputStream().read(buffer)
                        hello.complete(buffer.copyOf(read.coerceAtLeast(0)))
                    }
                }.onFailure { hello.completeExceptionally(it) }
            }
            val failed = CompletableFuture<String>()
            val connection = PahoTransport().open(
                host = host,
                port = server.localPort,
                tls = true,
                username = "user",
                password = "password",
                clientId = "sni-test",
                keepAliveSeconds = 30,
                onConnected = {},
                onFailed = { failed.complete(it) },
                onMessage = { _, _ -> },
                onDisconnected = {},
            )
            try {
                val bytes = hello.get(10, TimeUnit.SECONDS)
                assertEquals(TLS_HANDSHAKE_RECORD, bytes.first().toInt(), "the first bytes are not a TLS handshake")
                assertTrue(
                    bytes.toString(Charsets.ISO_8859_1).contains(host),
                    "the ClientHello does not carry the server name",
                )
                // The stand-in never answers, so the connect must end as a failure, not a hang.
                failed.get(30, TimeUnit.SECONDS)
            } finally {
                connection.close()
            }
        }
    }

    private companion object {
        const val TLS_HANDSHAKE_RECORD = 0x16
    }
}
