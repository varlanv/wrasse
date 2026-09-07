package com.varlanv.wrasse.testing

import java.nio.file.Files
import java.nio.file.Path

class SpecBody {
    val tests = mutableListOf<Pair<String, suspend () -> Unit>>()

    fun should(name: String, test: suspend () -> Unit) {
        tests.add(name to test)
    }
}

abstract class BaseSpec(body: SpecBody.() -> Unit) {
    val spec = SpecBody().apply(body)
}

suspend fun useTempDir(block: suspend (Path) -> Unit) {
    val dir = Files.createTempDirectory("wrasse-test-")
    try {
        block(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}
