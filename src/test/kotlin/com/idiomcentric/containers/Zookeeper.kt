package com.idiomcentric.containers

import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

object Zookeeper {
    private val log = KotlinLogging.logger { }
    class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

    val instance by lazy { startZookeeper() }
    private fun startZookeeper() = KGenericContainer("zookeeper:3.7.0").apply {
        withExposedPorts(2181)
        setWaitStrategy(HostPortWaitStrategy())
        withLogConsumer(Slf4jLogConsumer(log))
        start()
    }

    fun stop() {
        instance.stop()
    }

    private val host: String by lazy { instance.containerIpAddress }
    private val port: Int by lazy { instance.getMappedPort(2181) }
    val url: String by lazy { "$host:$port" }
}
