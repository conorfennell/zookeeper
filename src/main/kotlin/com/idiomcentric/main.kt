package com.idiomcentric

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.common.io.BaseEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import mu.withLoggingContext
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory.newClient
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.x.async.AsyncCuratorFramework
import org.apache.curator.x.async.modeled.JacksonModelSerializer
import org.apache.curator.x.async.modeled.ModelSpec
import org.apache.curator.x.async.modeled.ModeledFramework
import org.apache.curator.x.async.modeled.ZPath
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
private val log = KotlinLogging.logger { }

fun main() {
    runBlocking(Dispatchers.IO) {
        for (version in 30.downTo(1)) {
            launch {
                withLoggingContext(mapOf("VERSION" to version.toString())) {
                    withContext(MDCContext()) {
                        log.info { "GOOOOOOOO" }
                        checkAndUpdate(version, Square(1, version))
                    }
                }
            }
        }
    }
    log.info("ALL finished")
}

suspend fun hello(time: Long) {
    log.info("Started: $time")
    delay(time)
    log.info("Finished: $time")
}

suspend fun checkAndUpdate(version: Int, config: Square) {
    val configHash = config.toString().sha256()
    val zookeeperConfig = ZookeeperConfig(
        100,
        3,
        "127.0.0.1:2181",
        "/mutex/service/important",
        "/data/shared",
        30,
    )
    val retryPolicy: RetryPolicy = RetryNTimes(zookeeperConfig.maxRetries, zookeeperConfig.sleepMsBetweenRetries)
    val client: CuratorFramework = newClient(zookeeperConfig.connectString, retryPolicy)

    client.start()

    val mapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    val configSpec: ModelSpec<LastApplied> = ModelSpec.builder(
        ZPath.parseWithIds(zookeeperConfig.dataPath),
        JacksonModelSerializer<LastApplied>(mapper, mapper.typeFactory.constructType(LastApplied::class.java))
    ).build()
    val async = AsyncCuratorFramework.wrap(client)
    val lastAppliedClient: ModeledFramework<LastApplied> = ModeledFramework.wrap(async, configSpec)

    when (client.checkExists().forPath(zookeeperConfig.dataPath)) {
        null -> {
            client.createContainers(zookeeperConfig.dataPath)
            lastAppliedClient.set(LastApplied("FIRST_TIME_HASH", Instant.now())).await()
        }
    }

    val lastApplied = lastAppliedClient.read().await()

    if (lastApplied.hash != configHash) {
        lockRelease(zookeeperConfig, version, client) {
            val latestLastApplied = lastAppliedClient.read().await()
            log.info("Retrieved: $latestLastApplied")
            if (latestLastApplied.hash != configHash) {
                lastAppliedClient.set(LastApplied(configHash, Instant.now())).await()
            }
            log.info("Processed $version")
        }
    }
    client.close()
}

suspend fun lockRelease(config: ZookeeperConfig, version: Int, client: CuratorFramework, block: suspend () -> Unit) {
    val sharedLock = InterProcessSemaphoreMutex(client, config.lockPath)

    sharedLock.acquire(config.lockTimeout, TimeUnit.SECONDS)

    log.info("Acquired lock $version")
    try {
        block()
    } catch (exception: Exception) {
        log.error(exception) { "Error doing work" }
        throw exception
    } finally {
        sharedLock.release()
        log.info("Released lock $version")
    }
}

data class ZookeeperConfig(
    val sleepMsBetweenRetries: Int,
    val maxRetries: Int,
    val connectString: String,
    val lockPath: String,
    val dataPath: String,
    val lockTimeout: Long
)

fun String.sha256(): String {
    return hashString("SHA-256", this)
}

private fun hashString(type: String, input: String): String {
    val bytes = MessageDigest
        .getInstance(type)
        .digest(input.toByteArray())

    return BaseEncoding.base16().encode(bytes)
}

data class Square(@JsonSetter("x") val x: Int, @JsonSetter("y") val y: Int)

data class LastApplied(@JsonSetter("hash") val hash: String, @JsonSetter("updatedAt") val updatedAt: Instant)
