package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

private fun occurrence(source: String, text: String, canonicalIndex: Int): ModifierKeywordOccurrence {
    val start = source.indexOf(text)
    return ModifierKeywordOccurrence(canonicalIndex, start, start + text.length)
}

class ModifierOrderDecisionSpec : BaseSpec({

    should("report no violation for fewer than two comparable keywords") {
        val source = "private class Foo"
        val keywords = listOf(occurrence(source, "private", 2))

        ModifierOrderDecision.decide(keywords, source, hasComment = false) shouldBe null
    }

    should("report no violation for an empty keyword list") {
        ModifierOrderDecision.decide(emptyList(), "class Foo", hasComment = false) shouldBe null
    }

    should("report no violation when keywords are already canonically ordered") {
        val source = "public abstract suspend inline fun foo()"
        val keywords =
            listOf(
                occurrence(source, "public", 0),
                occurrence(source, "abstract", 8),
                occurrence(source, "suspend", 16),
                occurrence(source, "inline", 22),
            )

        ModifierOrderDecision.decide(keywords, source, hasComment = false) shouldBe null
    }

    should("swap two out-of-order keywords via one edit each, preserving the gap between them") {
        val source = "open private class Foo"
        val keywords = listOf(occurrence(source, "open", 7), occurrence(source, "private", 2))

        val verdict = ModifierOrderDecision.decide(keywords, source, hasComment = false)!!

        verdict.expectedOrder shouldBe "private open"
        verdict.reportStart shouldBe source.indexOf("open")
        verdict.reportEnd shouldBe source.indexOf(" class")
        verdict.edits.size shouldBe 2
        val (openSlot, privateSlot) = verdict.edits
        openSlot.startOffset shouldBe source.indexOf("open")
        openSlot.endOffset shouldBe source.indexOf("open") + "open".length
        openSlot.replacement shouldBe "private"
        privateSlot.startOffset shouldBe source.indexOf("private")
        privateSlot.endOffset shouldBe source.indexOf("private") + "private".length
        privateSlot.replacement shouldBe "open"

        val fixed =
            source.substring(0, openSlot.startOffset) + openSlot.replacement +
                source.substring(openSlot.endOffset, privateSlot.startOffset) + privateSlot.replacement +
                source.substring(privateSlot.endOffset)
        fixed shouldBe "private open class Foo"
    }

    should("resolve a full modifier soup permutation to canonical order") {
        val source = "suspend abstract inline public fun foo()"
        val keywords =
            listOf(
                occurrence(source, "suspend", 16),
                occurrence(source, "abstract", 8),
                occurrence(source, "inline", 22),
                occurrence(source, "public", 0),
            )

        val verdict = ModifierOrderDecision.decide(keywords, source, hasComment = false)!!

        verdict.expectedOrder shouldBe "public abstract suspend inline"
        verdict.edits.size shouldBe 3
    }

    should("leave a keyword whose canonical position already matches without an edit") {
        val source = "override tailrec fun foo()"
        val keywords = listOf(occurrence(source, "override", 12), occurrence(source, "tailrec", 14))

        ModifierOrderDecision.decide(keywords, source, hasComment = false) shouldBe null
    }

    should("emit no edits and report-only when a comment sits anywhere in the modifier list") {
        val source = "private /* c */ public class Foo"
        val keywords = listOf(occurrence(source, "private", 2), occurrence(source, "public", 0))

        val verdict = ModifierOrderDecision.decide(keywords, source, hasComment = true)!!

        verdict.expectedOrder shouldBe "public private"
        verdict.edits shouldBe emptyList()
    }

    should("leave annotations and unrecognized modifier-adjacent nodes untouched since they are never passed as keywords") {
        val source = "suspend @A override public @B fun foo() {}"
        val keywords =
            listOf(
                occurrence(source, "suspend", 16),
                occurrence(source, "override", 12),
                occurrence(source, "public", 0),
            )

        val verdict = ModifierOrderDecision.decide(keywords, source, hasComment = false)!!

        verdict.expectedOrder shouldBe "public override suspend"
        verdict.edits.size shouldBe 2
        val (suspendSlot, publicSlot) = verdict.edits
        suspendSlot.startOffset shouldBe source.indexOf("suspend")
        suspendSlot.replacement shouldBe "public"
        publicSlot.startOffset shouldBe source.indexOf("public")
        publicSlot.replacement shouldBe "suspend"
    }

    should("swap the visibility and expect/actual pair independently") {
        val source = "actual expect class Foo"
        val keywords = listOf(occurrence(source, "actual", 5), occurrence(source, "expect", 4))

        val verdict = ModifierOrderDecision.decide(keywords, source, hasComment = false)!!

        verdict.expectedOrder shouldBe "expect actual"
        verdict.edits.size shouldBe 2
    }
})
