package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class EditPlanSpec : BaseSpec({

    should("return finalEdits ordered by ascending span") {
        val plan = EditPlan()
        plan.add("rule-b", WEdit(20, 21, "y"))
        plan.add("rule-a", WEdit(5, 6, "x"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 2
        edits[0].startOffset shouldBe 5
        edits[1].startOffset shouldBe 20
    }

    should("keep many edits sorted by span, later-added first among identical spans") {
        val plan = EditPlan()
        val spans = (0 until 200).map { i -> (i * 7_919) % 200 }
        for ((i, s) in spans.withIndex()) plan.add("r$i", WEdit(s * 2, s * 2 + 1, "v$i"))
        plan.add("dup-first", WEdit(100, 101, "first"))
        plan.add("dup-second", WEdit(100, 101, "second"))

        val edits = plan.takeAll()

        edits.map { it.edit.startOffset }.zipWithNext().all { (a, b) -> a <= b } shouldBe true
        edits
            .filter { it.edit.startOffset == 100 }
            .map { it.ruleId } shouldBe listOf("dup-second", "dup-first", "r${spans.indexOf(50)}")
    }

    should("keep one copy of an identical edit two rules both emit, but keep differing edits on the same span") {
        val plan = EditPlan()
        plan.add("named-arguments", WEdit(10, 10, "x = "))
        plan.add("no-mixed-named-positional-arguments", WEdit(10, 10, "x = "))
        plan.add("other", WEdit(10, 10, "y = "))

        val edits = plan.finalEdits()

        edits.map { it.replacement } shouldBe listOf("y = ", "x = ")
    }

    should("take and remove edits whose span lies within the requested range") {
        val plan = EditPlan()
        plan.add("inner", WEdit(3, 4, "x"))
        plan.add("outside", WEdit(3, 12, "y"))

        val taken = plan.takeEditsIn(0, 10)

        taken shouldHaveSize 1
        taken[0].ruleId shouldBe "inner"
        taken[0].edit.startOffset shouldBe 3
        taken[0].edit.endOffset shouldBe 4

        val remaining = plan.finalEdits()
        remaining shouldHaveSize 1
        remaining[0].startOffset shouldBe 3
        remaining[0].endOffset shouldBe 12
    }

    should("take edits touching the exact boundary of the requested range") {
        val plan = EditPlan()
        plan.add("inner", WEdit(0, 10, "x"))

        val taken = plan.takeEditsIn(0, 10)

        taken shouldHaveSize 1
        plan.finalEdits().shouldBeEmpty()
    }

    should("leave the plan empty after a composing rule consumes every inner edit") {
        val plan = EditPlan()
        plan.add("inner", WEdit(3, 4, "x"))
        plan.takeEditsIn(0, 10)

        plan.finalEdits().shouldBeEmpty()
    }

    should("order same-offset zero-width inserts by descending collection sequence") {
        val plan = EditPlan()
        plan.add("rule-1", WEdit(5, 5, "A"))
        plan.add("rule-2", WEdit(5, 5, "B"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 2
        edits[0].replacement shouldBe "B"
        edits[1].replacement shouldBe "A"
    }

    should("drop the later-starting edit and keep the earlier one on a partial overlap") {
        val plan = EditPlan()
        plan.add("rule-a", WEdit(5, 10, "AAA"))
        plan.add("rule-b", WEdit(8, 12, "BBB"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe "AAA"
        plan.droppedEdits() shouldHaveSize 1
        plan.droppedEdits()[0].ruleId shouldBe "rule-b"
        plan.droppedEdits()[0].startOffset shouldBe 8
        plan.droppedEdits()[0].endOffset shouldBe 12
    }

    should("drop a nested inner edit and keep the enclosing outer edit") {
        val plan = EditPlan()
        plan.add("outer", WEdit(5, 20, "OUTER"))
        plan.add("inner", WEdit(8, 12, "inner"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe "OUTER"
        plan.droppedEdits().map { it.ruleId } shouldBe listOf("inner")
    }

    should("drop an inner edit nested inside a multi-line outer edit") {
        val plan = EditPlan()
        plan.add("outer", WEdit(0, 20, "line one\nline two"))
        plan.add("inner", WEdit(5, 8, "x"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe "line one\nline two"
        plan.droppedEdits().map { it.ruleId } shouldBe listOf("inner")
    }

    should("keep the earliest-collected edit when two rules emit different replacements for an identical span") {
        val plan = EditPlan()
        plan.add("rule-a", WEdit(5, 10, "AAA"))
        plan.add("rule-b", WEdit(5, 10, "BBB"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe "AAA"
        plan.droppedEdits().map { it.ruleId } shouldBe listOf("rule-b")
    }

    should("not treat a zero-width insert at the start of a later edit as an overlap") {
        val plan = EditPlan()
        plan.add("insert-rule", WEdit(5, 5, "X"))
        plan.add("replace-rule", WEdit(5, 10, "YYY"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 2
        edits[0].replacement shouldBe "X"
        edits[1].replacement shouldBe "YYY"
        plan.droppedEdits().shouldBeEmpty()
    }

    should("keep two non-zero-width edits that only touch at a shared boundary") {
        val plan = EditPlan()
        plan.add("left", WEdit(0, 5, "LEFT"))
        plan.add("right", WEdit(5, 10, "RIGHT"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 2
        edits.map { it.replacement } shouldBe listOf("LEFT", "RIGHT")
        plan.droppedEdits().shouldBeEmpty()
    }

    should("drop an entire brace-insertion pair when only its OPEN edit overlaps a kept edit") {
        val plan = EditPlan()
        plan.add("other", WEdit(5, 12, "OTHER"))
        val braceGroup = plan.newGroupId()
        plan.add("brace", WEdit(10, 11, " {\n"), braceGroup)
        plan.add("brace", WEdit(40, 41, "\n}"), braceGroup)

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe "OTHER"
        plan.droppedEdits().map { it.ruleId } shouldBe listOf("brace", "brace")
        plan.droppedEdits().map { it.startOffset to it.endOffset } shouldBe listOf(10 to 11, 40 to 41)
    }

    should("keep a brace-insertion pair and an edit nested in the gap between OPEN and CLOSE") {
        val plan = EditPlan()
        val braceGroup = plan.newGroupId()
        plan.add("brace", WEdit(10, 11, " {\n"), braceGroup)
        plan.add("brace", WEdit(40, 41, "\n}"), braceGroup)
        plan.add("inner", WEdit(15, 20, "INNER"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 3
        edits.map { it.replacement } shouldBe listOf(" {\n", "INNER", "\n}")
        plan.droppedEdits().shouldBeEmpty()
    }

    should("keep two independent brace-insertion pairs whose edits interleave in span order") {
        val plan = EditPlan()
        val pairA = plan.newGroupId()
        val pairB = plan.newGroupId()
        plan.add("pair-a", WEdit(10, 11, "openA"), pairA)
        plan.add("pair-b", WEdit(15, 16, "openB"), pairB)
        plan.add("pair-a", WEdit(20, 21, "closeA"), pairA)
        plan.add("pair-b", WEdit(25, 26, "closeB"), pairB)

        val edits = plan.finalEdits()

        edits.map { it.replacement } shouldBe listOf("openA", "openB", "closeA", "closeB")
        plan.droppedEdits().shouldBeEmpty()
    }

    should("not let a multi-edit group's edit be swallowed by an identical singleton from another group") {
        val plan = EditPlan()
        plan.add("other", WEdit(10, 11, " {\n"))
        val braceGroup = plan.newGroupId()
        plan.add("brace", WEdit(10, 11, " {\n"), braceGroup, groupSize = 2)
        plan.add("brace", WEdit(40, 41, "\n}"), braceGroup, groupSize = 2)

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        edits[0].replacement shouldBe " {\n"
        plan.droppedEdits().map { it.ruleId } shouldBe listOf("brace", "brace")
        plan.droppedEdits().map { it.startOffset to it.endOffset } shouldBe listOf(10 to 11, 40 to 41)
    }

    should("still dedup an identical singleton edit two single-edit groups both emit") {
        val plan = EditPlan()
        plan.add("rule-a", WEdit(10, 11, " {\n"), groupSize = 1)
        plan.add("rule-b", WEdit(10, 11, " {\n"), groupSize = 1)

        val edits = plan.finalEdits()

        edits shouldHaveSize 1
        plan.droppedEdits().shouldBeEmpty()
    }

    should("resolve a large non-overlapping plan and keep every edit") {
        val plan = EditPlan()
        for (i in 0 until 5_000) plan.add("rule-$i", WEdit(i * 10, i * 10 + 5, "x"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 5_000
        plan.droppedEdits().shouldBeEmpty()
    }

    should("resolve a file-spanning kept group against 5,000 later singletons quickly") {
        val plan = EditPlan()
        val spanGroup = plan.newGroupId()
        for (i in 0 until 5_000) plan.add("span", WEdit(i * 10, i * 10 + 5, "x"), spanGroup, groupSize = 5_000)
        for (i in 0 until 5_000) plan.add("rule-$i", WEdit(i * 10 + 2, i * 10 + 7, "y"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 5_000
        plan.droppedEdits() shouldHaveSize 5_000
    }
})
