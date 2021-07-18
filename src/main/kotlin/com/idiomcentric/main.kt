package com.idiomcentric

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory.newClient
import org.apache.curator.framework.api.transaction.CuratorMultiTransaction
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex
import org.apache.curator.retry.RetryNTimes
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// TODO: Use async library convert to coroutines
// TODO: Include a timestamp for when the change was made, use a data class
fun main() {
    runBlocking(Dispatchers.IO) {
        for (version in 1..100) {
            launch {
                checkAndUpdate(version, Square(1, version))
            }
        }
    }
}

fun checkAndUpdate(version: Int, config: Square) {
    val zookeeperConfig = ZookeeperConfig(
        100,
        3,
        "127.0.0.1:2181",
        "/root/shared",
        "/root/shared",
        30,
    )
    val retryPolicy: RetryPolicy = RetryNTimes(zookeeperConfig.maxRetries, zookeeperConfig.sleepMsBetweenRetries)
    val client: CuratorFramework = newClient(zookeeperConfig.connectString, retryPolicy)

    client.start()

    when (client.checkExists().forPath(zookeeperConfig.dataPath)) {
        null -> client.createContainers(zookeeperConfig.dataPath)
    }

    if (String(client.data.forPath(zookeeperConfig.dataPath), UTF_8) != config.toString().sha256()) {
        lockRelease(zookeeperConfig, version, client) {
            val bytes: ByteArray = client.data.forPath(zookeeperConfig.dataPath)

            println("Retrieved: ${String(bytes, UTF_8)}")

            if (String(bytes, UTF_8) != config.toString().sha256()) {
                val updateData = client.transactionOp().setData()
                    .forPath(zookeeperConfig.dataPath, config.toString().sha256().toByteArray())
                val transaction: CuratorMultiTransaction = client.transaction()

                transaction.forOperations(updateData)
            }
            println("Processed $version")
        }
    }
    client.close()
}

fun lockRelease(config: ZookeeperConfig, version: Int, client: CuratorFramework, block: () -> Unit) {
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

data class Square(val x: Int, val y: Int)
