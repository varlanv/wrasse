package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

private fun record(
    fqn: String,
    aliasName: String? = null,
    startOffset: Int = 0,
    endOffset: Int = fqn.length,
): ImportRecord =
    ImportRecord(
        fqn = fqn,
        simpleName = fqn.substringAfterLast('.'),
        aliasName = aliasName,
        startOffset = startOffset,
        endOffset = endOffset,
    )

class UnusedImportDecisionSpec : BaseSpec({

    should("mark unused when nothing matches by classifier, callable, or comment") {
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.text.Regex"),
            classifiers = setOf("kotlin.Int"),
            callables = emptySet(),
            sourceText = "package sample",
            commentSpans = emptyList(),
        )
        unused shouldBe true
    }

    should("mark used on exact classifier FQN match") {
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.text.Regex"),
            classifiers = setOf("kotlin.text.Regex"),
            callables = emptySet(),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("mark used when a classifier is a nested member of the imported FQN") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Outer"),
            classifiers = setOf("sample.aux.Outer.Nested"),
            callables = emptySet(),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("not match a classifier that merely shares a prefix string without a dot boundary") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Outer"),
            classifiers = setOf("sample.aux.OuterThing"),
            callables = emptySet(),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe true
    }

    should("mark used on exact callable classFqName match (constructor/companion/enum-entry shape)") {
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.Pair"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "kotlin", classFqName = "kotlin.Pair", name = "Pair")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("mark used when a callable's classFqName is a nested/companion member of the imported FQN") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Outer"),
            classifiers = emptySet(),
            callables = setOf(
                WCallableUsage(packageFqName = "sample.aux", classFqName = "sample.aux.Outer.Companion", name = "value")
            ),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("mark used on a top-level callable match by package and simple name") {
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.math.abs"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "kotlin.math", classFqName = null, name = "abs")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("not match a top-level callable in a different package with the same simple name") {
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.math.abs"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "kotlin.other", classFqName = null, name = "abs")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe true
    }

    should("mark used on a member import (object val/fun) matched by parent classFqName and simple name") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Obj.member"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "sample.aux", classFqName = "sample.aux.Obj", name = "member")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("mark used on an enum-entry import used as a bare entry name") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Color.RED"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "sample.aux", classFqName = "sample.aux.Color", name = "RED")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe false
    }

    should("not match a member import against a member of a different class with the same simple name") {
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.Obj.member"),
            classifiers = emptySet(),
            callables = setOf(WCallableUsage(packageFqName = "sample.aux", classFqName = "sample.aux.OtherObj", name = "member")),
            sourceText = "",
            commentSpans = emptyList(),
        )
        unused shouldBe true
    }

    should("mark used when the simple name appears as a whole word inside a recorded comment span") {
        val text = "// See [kotlin.text.Regex] for details."
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.text.Regex"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = listOf(0 until text.length),
        )
        unused shouldBe false
    }

    should("mark unused when the simple name only occurs inside a longer identifier in a comment") {
        val text = "// See RegexOption for details."
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.text.Regex"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = listOf(0 until text.length),
        )
        unused shouldBe true
    }

    should("check the alias name in comments instead of the simple name when an alias is present") {
        val text = "// see myAbs here"
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.math.abs", aliasName = "myAbs"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = listOf(0 until text.length),
        )
        unused shouldBe false
    }

    should("mark used when a space-containing (backtick-derived) simple name appears as a whole phrase in a comment") {
        val text = "// see the weird fun helper for details"
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.weird fun"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = listOf(0 until text.length),
        )
        unused shouldBe false
    }

    should("mark unused when a space-containing simple name is only a prefix of a longer word run in a comment") {
        val text = "// see the weird function for details"
        val unused = UnusedImportDecision.isUnused(
            import = record("sample.aux.weird fun"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = listOf(0 until text.length),
        )
        unused shouldBe true
    }

    should("ignore a comment-only match outside the recorded comment spans") {
        val text = "Regex used in real code, not a comment span"
        val unused = UnusedImportDecision.isUnused(
            import = record("kotlin.text.Regex"),
            classifiers = emptySet(),
            callables = emptySet(),
            sourceText = text,
            commentSpans = emptyList(),
        )
        unused shouldBe true
    }
})
