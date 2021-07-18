package com.idiomcentric

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

fun main() {
    runBlocking(Dispatchers.IO) {
        for (version in 1..50) {
            launch {
                checkAndUpdate(version, Square(1, version))
            }
        }
    }
}

suspend fun checkAndUpdate(version: Int, config: Square) {
    val configHash = config.toString().sha256()
    val zookeeperConfig = ZookeeperConfig(
        100,
        3,
        "127.0.0.1:2181",
        "/mutex/service/important",
        "/root/modeled",
        30,
    )
    val retryPolicy: RetryPolicy = RetryNTimes(zookeeperConfig.maxRetries, zookeeperConfig.sleepMsBetweenRetries)
    val client: CuratorFramework = newClient(zookeeperConfig.connectString, retryPolicy)

    client.start()

    val mapper = ObjectMapper()
    mapper.registerModule(JavaTimeModule())

    val configSpec: ModelSpec<MetaConfig> = ModelSpec.builder(
        ZPath.parseWithIds(zookeeperConfig.dataPath),
        JacksonModelSerializer<MetaConfig>(mapper, mapper.typeFactory.constructType(MetaConfig::class.java))
    ).build()
    val async = AsyncCuratorFramework.wrap(client)
    val modeledClient: ModeledFramework<MetaConfig> = ModeledFramework.wrap(async, configSpec)

    when (client.checkExists().forPath(zookeeperConfig.dataPath)) {
        null -> client.createContainers(zookeeperConfig.dataPath)
    }

    val metaConfig = modeledClient.read().await()

    if (metaConfig.hash != configHash) {
        lockRelease(zookeeperConfig, version, client) {
            val metaConfig = modeledClient.read().await()
            println("Retrieved: $metaConfig")
            if (metaConfig.hash != configHash) {
                modeledClient.set(MetaConfig(configHash, Instant.now())).await()
            }
            println("Processed $version")
        }
    }
    client.close()
}

suspend fun lockRelease(config: ZookeeperConfig, version: Int, client: CuratorFramework, block: suspend () -> Unit) {
    val sharedLock = InterProcessSemaphoreMutex(client, config.lockPath)

    sharedLock.acquire(config.lockTimeout, TimeUnit.SECONDS)

    println("Acquired lock $version")
    try {
        block()
    } catch (ex: Exception) {
        println(ex)
    } finally {
        sharedLock.release()
        println("Released lock $version")
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

const val HEX_CHARS = "0123456789ABCDEF"

private fun hashString(type: String, input: String): String {
    val bytes = MessageDigest
        .getInstance(type)
        .digest(input.toByteArray())
    val result = StringBuilder(bytes.size * 2)

    StringBuilder(bytes.size * 2).let { }

    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr 4 and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }
    return result.toString()
}


data class Square(@JsonSetter("x") val x: Int, @JsonSetter("y") val y: Int)

data class MetaConfig(@JsonSetter("hash") val hash: String, @JsonSetter("updatedAt") val updatedAt: Instant)

