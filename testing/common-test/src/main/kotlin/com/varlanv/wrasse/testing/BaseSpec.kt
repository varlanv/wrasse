package com.varlanv.wrasse.testing

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.concurrency.TestExecutionMode
import java.nio.file.Files
import java.nio.file.Path

abstract class BaseSpec(
    body: ShouldSpec.() -> Unit,
) : ShouldSpec({
        isolationMode = IsolationMode.SingleInstance
        testExecutionMode = TestExecutionMode.LimitedConcurrency(Runtime.getRuntime().availableProcessors())
        body()
    })

suspend fun useTempDir(block: suspend (Path) -> Unit) {
    val dir = Files.createTempDirectory("wrasse-test-")
    try {
        block(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}
