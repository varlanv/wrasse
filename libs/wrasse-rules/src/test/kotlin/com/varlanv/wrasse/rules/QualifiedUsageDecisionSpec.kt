package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WQualifiedUsageKind
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private fun qualifier(sourceText: String, written: String, targetFqName: String, packageFqName: String): WQualifiedUsage {
    val start = sourceText.indexOf(written)
    return WQualifiedUsage(start, start + written.length, targetFqName, packageFqName, WQualifiedUsageKind.QUALIFIER)
}

private fun typeRef(sourceText: String, written: String, targetFqName: String, packageFqName: String): WQualifiedUsage {
    val start = sourceText.indexOf(written)
    return WQualifiedUsage(start, start + written.length, targetFqName, packageFqName, WQualifiedUsageKind.TYPE_REF)
}

private fun explicitImport(fqn: String, aliasName: String? = null): ImportRecord =
    ImportRecord(fqn = fqn, simpleName = fqn.substringAfterLast('.'), aliasName = aliasName, startOffset = 0, endOffset = 0)

class QualifiedUsageDecisionSpec : BaseSpec({

    should("report a fully-qualified qualifier with no import and no collision") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
        reports[0].dropStart shouldBe sourceText.indexOf("a.b.C")
        reports[0].dropEnd shouldBe sourceText.indexOf("a.b.C") + "a.b.".length
    }

    should("prove a TYPE_REF usage whose written span includes generic arguments") {
        val sourceText = "val list: a.b.C<Int> = TODO()"
        val usage = typeRef(sourceText, "a.b.C<Int>", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
        reports[0].dropEnd shouldBe sourceText.indexOf("a.b.C<Int>") + "a.b.".length
    }

    should("prove a TYPE_REF usage whose written span includes a nullability marker") {
        val sourceText = "val c: a.b.C? = null"
        val usage = typeRef(sourceText, "a.b.C?", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
    }

    should("drop only the package prefix for a nested-class TYPE_REF, proposing the outer class as the import") {
        val sourceText = "val n: a.b.C.Nested = TODO()"
        val usage = typeRef(sourceText, "a.b.C.Nested", "a.b.C.Nested", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C.Nested"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
        val start = sourceText.indexOf("a.b.C.Nested")
        reports[0].dropStart shouldBe start
        reports[0].dropEnd shouldBe start + "a.b.".length
    }

    should("skip a usage whose written chain does not equal the target FQN (backtick-quoted segment)") {
        val sourceText = "val c = a.b.`C`"
        val usage = qualifier(sourceText, "a.b.`C`", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("skip a usage whose class is in the root package") {
        val sourceText = "val c = C"
        val usage = qualifier(sourceText, "C", "C", "")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("report unconditionally when a plain explicit import of the candidate already exists, bypassing collision checks") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C", "x.y.C"),
            callables = emptySet(),
            explicitImports = listOf(explicitImport("a.b.C")),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
    }

    should("not treat an aliased explicit import of the same FQN as already-imported (no bare name in scope)") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = listOf(explicitImport("a.b.C", aliasName = "D")),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
    }

    should("skip a same-package usage (no explicit import at all) when an unrelated explicit import shadows the same simple name") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "a.b",
            classifiers = setOf("a.b.C", "x.y.C"),
            callables = emptySet(),
            explicitImports = listOf(explicitImport("x.y.C")),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("report a same-package usage (no explicit import at all) when nothing shadows it") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "a.b",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 1
    }

    should("not skip a same-package usage just because its own class declaration writes the same simple name elsewhere") {
        val sourceText = "class C\nval c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")
        val declarationOffset = sourceText.indexOf("C")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "a.b",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = listOf(IdentifierOccurrence("C", declarationOffset)),
        )

        reports shouldHaveSize 1
    }

    should("skip when the simple name collides with a different used FQN's own simple name") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C", "x.y.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("skip when an explicit import's visible name binds the same simple name to a different FQN") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = listOf(explicitImport("x.y.C", aliasName = "C")),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("skip when the simple name is written elsewhere in the file outside this usage's own span") {
        val sourceText = "val x = C; val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")
        val elsewhereOffset = sourceText.indexOf("C")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = listOf(IdentifierOccurrence("C", elsewhereOffset)),
        )

        reports.shouldBeEmpty()
    }

    should("not skip when the simple name appears only inside this usage's own span") {
        val sourceText = "val c = a.b.C"
        val usage = qualifier(sourceText, "a.b.C", "a.b.C", "a.b")
        val ownOffset = sourceText.indexOf("C", usage.startOffset)

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = listOf(IdentifierOccurrence("C", ownOffset)),
        )

        reports shouldHaveSize 1
    }

    should("report every usage of the same target once the shared per-target decision allows it") {
        val sourceText = "val c1 = a.b.C\nval c2 = a.b.C"
        val firstOffset = sourceText.indexOf("a.b.C")
        val secondOffset = sourceText.indexOf("a.b.C", firstOffset + 1)
        val usages = listOf(
            WQualifiedUsage(firstOffset, firstOffset + 5, "a.b.C", "a.b", WQualifiedUsageKind.QUALIFIER),
            WQualifiedUsage(secondOffset, secondOffset + 5, "a.b.C", "a.b", WQualifiedUsageKind.QUALIFIER),
        )

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = usages,
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports shouldHaveSize 2
        reports.map { it.dropStart } shouldBe listOf(firstOffset, secondOffset)
    }

    should("not skip when the simple name's only other occurrence is a fully-qualified constructor call of the same target") {
        val sourceText = "val b: a.b.C<Int> = a.b.C(1)"
        val usage = typeRef(sourceText, "a.b.C<Int>", "a.b.C", "a.b")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = listOf(IdentifierOccurrence("C", sourceText.indexOf("(") - 1)),
        )

        reports shouldHaveSize 1
    }

    should("skip every usage of a shared target when the shared decision bails") {
        val sourceText = "val c1 = a.b.C\nval c2 = a.b.C"
        val firstOffset = sourceText.indexOf("a.b.C")
        val secondOffset = sourceText.indexOf("a.b.C", firstOffset + 1)
        val usages = listOf(
            WQualifiedUsage(firstOffset, firstOffset + 5, "a.b.C", "a.b", WQualifiedUsageKind.QUALIFIER),
            WQualifiedUsage(secondOffset, secondOffset + 5, "a.b.C", "a.b", WQualifiedUsageKind.QUALIFIER),
        )

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = usages,
            sourceText = sourceText,
            filePackageFqName = "other",
            classifiers = setOf("a.b.C", "x.y.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }

    should("skip a same-package candidate when a local class records the same simple name as a <local> classifier") {
        val sourceText = "val c = sample.C"
        val usage = qualifier(sourceText, "sample.C", "sample.C", "sample")

        val reports = QualifiedUsageDecision.decideAll(
            qualifiedUsages = listOf(usage),
            sourceText = sourceText,
            filePackageFqName = "sample",
            classifiers = setOf("sample.C", "<local>.C"),
            callables = emptySet(),
            explicitImports = emptyList(),
            identifierOccurrences = emptyList(),
        )

        reports.shouldBeEmpty()
    }
})
