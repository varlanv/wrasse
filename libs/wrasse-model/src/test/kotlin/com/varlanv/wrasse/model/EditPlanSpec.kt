package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
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
        val spans = (0 until 200).map { i -> (i * 7919) % 200 }
        for ((i, s) in spans.withIndex()) plan.add("r$i", WEdit(s * 2, s * 2 + 1, "v$i"))
        plan.add("dup-first", WEdit(100, 101, "first"))
        plan.add("dup-second", WEdit(100, 101, "second"))

        val edits = plan.takeAll()

        edits.map { it.edit.startOffset }.zipWithNext().all { (a, b) -> a <= b } shouldBe true
        edits.filter { it.edit.startOffset == 100 }.map { it.ruleId } shouldBe listOf("dup-second", "dup-first", "r${spans.indexOf(50)}")
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

    should("fail loudly with full rule attribution when surviving edits overlap") {
        val plan = EditPlan()
        plan.add("rule-a", WEdit(5, 10, "AAA"))
        plan.add("rule-b", WEdit(8, 12, "BBB"))

        val exception = shouldThrow<IllegalStateException> {
            plan.finalEdits()
        }

        exception.message shouldBe
            "EditPlan: overlapping edits from rule 'rule-a' (5..10 -> \"AAA\") and rule 'rule-b' (8..12 -> \"BBB\")"
    }

    should("not treat a zero-width insert at the start of a later edit as an overlap") {
        val plan = EditPlan()
        plan.add("insert-rule", WEdit(5, 5, "X"))
        plan.add("replace-rule", WEdit(5, 10, "YYY"))

        val edits = plan.finalEdits()

        edits shouldHaveSize 2
        edits[0].replacement shouldBe "X"
        edits[1].replacement shouldBe "YYY"
    }
})
