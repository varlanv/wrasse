package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class FormatRequestSpec : BaseSpec({
    val now = 1_000_000_000L

    should("accept a fresh request") {
        FormatRequest.parse(listOf("timestamp=$now", "formatting=true"), now + 1_000).formatting shouldBe true
    }

    should("accept a request exactly at the age limit and reject one past it") {
        val lines = listOf("timestamp=$now", "formatting=true")
        FormatRequest.parse(lines, now + FormatRequest.MAX_AGE_MILLIS).formatting shouldBe true
        FormatRequest.parse(lines, now + FormatRequest.MAX_AGE_MILLIS + 1).formatting shouldBe false
    }

    should("reject a request without a timestamp, with a malformed one, or without formatting=true") {
        FormatRequest.parse(listOf("formatting=true"), now).formatting shouldBe false
        FormatRequest.parse(listOf("timestamp=soon", "formatting=true"), now).formatting shouldBe false
        FormatRequest.parse(listOf("timestamp=$now", "formatting=false"), now).formatting shouldBe false
        FormatRequest.parse(listOf("timestamp=$now"), now).formatting shouldBe false
        FormatRequest.parse(emptyList(), now).formatting shouldBe false
    }

    should("read debugPerformance independently of formatting") {
        val request = FormatRequest.parse(listOf("timestamp=$now", "debugPerformance=true"), now)
        request.formatting shouldBe false
        request.debugPerformance shouldBe true
        FormatRequest.parse(listOf("timestamp=$now", "formatting=true"), now).debugPerformance shouldBe false
        FormatRequest.parse(listOf("timestamp=0", "debugPerformance=true"), now).debugPerformance shouldBe false
    }

    should("tolerate whitespace around keys and values and lines without =") {
        FormatRequest.parse(listOf("# comment", " timestamp = $now ", "formatting = true"), now).formatting shouldBe
            true
    }

    should("keep a fresh request available for repeated compiles") {
        useTempDir { dir ->
            FormatRequest.write(dir, now, debugPerformance = true)
            Files.exists(dir.resolve(FormatRequest.FILE_NAME)) shouldBe true
            val request = FormatRequest.read(dir, now + 1)
            request.formatting shouldBe true
            request.debugPerformance shouldBe true
            Files.exists(dir.resolve(FormatRequest.FILE_NAME)) shouldBe true
            FormatRequest.read(dir, now + 1).formatting shouldBe true
        }
    }

    should("ignore a stale request") {
        useTempDir { dir ->
            FormatRequest.write(dir, now)
            FormatRequest.read(dir, now + FormatRequest.MAX_AGE_MILLIS + 1).formatting shouldBe false
            Files.exists(dir.resolve(FormatRequest.FILE_NAME)) shouldBe true
        }
    }

    should("return no request for a directory that does not exist") {
        useTempDir { dir ->
            FormatRequest.read(dir.resolve("missing"), now).formatting shouldBe false
        }
    }

    should("carry the quiet flag") {
        FormatRequest.parse(listOf("timestamp=1000", "quiet=true"), now = 1_000).quiet shouldBe true
        FormatRequest.parse(listOf("timestamp=1000", "formatting=true"), now = 1_000).quiet shouldBe false
    }
})
