package com.idiomcentric

import com.idiomcentric.containers.Zookeeper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleTest {

    @BeforeAll
    fun setup() {
        println(Zookeeper.instance)
    }

    @AfterAll
    fun tearDown() {
        Zookeeper.stop()
    }

    @Test
    fun testing() {
        main(Zookeeper.url)
    }
}
