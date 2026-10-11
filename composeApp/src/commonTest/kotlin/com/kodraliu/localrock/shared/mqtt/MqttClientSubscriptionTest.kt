package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MqttClientSubscriptionTest {

    private class FakeConnection : MqttNativeConnection {
        val unsubscribed = mutableListOf<String>()
        override val isConnected: Boolean = true
        override fun subscribe(topic: String, qos: Int) {}
        override fun unsubscribe(topic: String) { unsubscribed += topic }
        override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {}
        override fun close() {}
    }

    private class FakeTransport(private val connection: MqttNativeConnection) : MqttNativeTransport {
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            onConnected()
            return connection
        }
    }

    private val creds = MqttCreds(host = "localhost", port = 8883, tls = true, username = "u", password = "p")

    @Test
    fun topic_stays_subscribed_until_the_last_holder_leaves() = runTest {
        val connection = FakeConnection()
        val client = MqttClient(FakeTransport(connection))
        client.start(creds, clientId = "test")
        client.connected.first { it }
        try {
            client.subscribe("rr/m/o/a/b/robot")
            client.subscribe("rr/m/o/a/b/robot")

            client.unsubscribe("rr/m/o/a/b/robot")
            assertEquals(emptyList(), connection.unsubscribed)

            client.unsubscribe("rr/m/o/a/b/robot")
            assertEquals(listOf("rr/m/o/a/b/robot"), connection.unsubscribed)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun unsubscribing_a_topic_nobody_holds_does_nothing() = runTest {
        val connection = FakeConnection()
        val client = MqttClient(FakeTransport(connection))
        client.start(creds, clientId = "test")
        client.connected.first { it }
        try {
            client.unsubscribe("rr/m/o/a/b/robot")
            assertEquals(emptyList(), connection.unsubscribed)
        } finally {
            client.disconnect()
        }
    }
}
