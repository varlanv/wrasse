package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class ForbiddenCallsDecisionSpec : BaseSpec({

    fun site(pkg: String, cls: String?, name: String, constructor: Boolean = false) = WCallSite(0, 1, pkg, cls, name, true, emptyList(), constructor)

    should("build the canonical name of a member, a top-level callable, and a constructor") {
        ForbiddenCallsDecision.canonicalName(site("java.lang", "java.lang.System", "currentTimeMillis")) shouldBe "java.lang.System.currentTimeMillis"
        ForbiddenCallsDecision.canonicalName(site("kotlin.collections", null, "associateBy")) shouldBe "kotlin.collections.associateBy"
        ForbiddenCallsDecision.canonicalName(site("java.util", "java.util.Date", "Date", constructor = true)) shouldBe "java.util.Date"
    }

    should("match exactly, or by prefix with a trailing star") {
        val exact = ForbiddenCallsDecision.compile(mapOf("kotlin.collections.associateBy" to emptyList())).single()
        exact.matches("kotlin.collections.associateBy") shouldBe true
        exact.matches("kotlin.collections.associateByTo") shouldBe false
        val prefix = ForbiddenCallsDecision.compile(mapOf("kotlin.collections.associate*" to emptyList())).single()
        prefix.matches("kotlin.collections.associateBy") shouldBe true
        prefix.matches("kotlin.collections.associate") shouldBe true
        prefix.matches("kotlin.collections.map") shouldBe false
    }

    should("allow a pattern in the files matching its globs, relative to the config directory") {
        val call = ForbiddenCallsDecision.compile(mapOf("java.lang.System.currentTimeMillis" to listOf("**/SystemClock.kt"))).single()
        call.allowedIn(Path.of("src/main/kotlin/app/SystemClock.kt")) shouldBe true
        call.allowedIn(Path.of("src/main/kotlin/app/Other.kt")) shouldBe false
        ForbiddenCallsDecision.compile(mapOf("a.b" to emptyList())).single().allowedIn(Path.of("x.kt")) shouldBe false
    }

    should("word the message around the canonical name") {
        ForbiddenCallsDecision.message("java.lang.System.currentTimeMillis") shouldBe "Call to 'java.lang.System.currentTimeMillis' is forbidden"
    }
})
