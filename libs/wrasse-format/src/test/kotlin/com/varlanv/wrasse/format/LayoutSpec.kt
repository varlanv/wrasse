package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LayoutSpec : BaseSpec({

    val style = FormatStyle(indentWidth = 4, maxLineLength = 20)

    should("render Text verbatim") {
        Layout.render(Doc.Text("val x = 1"), style) shouldBe "val x = 1"
    }

    should("render a HARD break as its literal plus the ambient indent") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("{"),
                Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("x")))),
                Doc.Break(BreakKind.HARD, literal = "\n"),
                Doc.Text("}"),
            ),
        )
        Layout.render(doc, style) shouldBe "{\n    x\n}"
    }

    should("preserve a HARD break's literal prefix verbatim, including blank lines") {
        val doc = Doc.Concat(listOf(Doc.Text("a"), Doc.Break(BreakKind.HARD, literal = "\n\n"), Doc.Text("b")))
        Layout.render(doc, style) shouldBe "a\n\nb"
    }

    should("nest Indent depth additively") {
        val doc = Doc.Indent(Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("x")))))
        Layout.render(doc, style) shouldBe "\n        x"
    }

    should("render a Group flat when its content fits the remaining width") {
        val doc = Doc.Group(Doc.Concat(listOf(Doc.Text("a"), Doc.Break(BreakKind.SOFT), Doc.Text("b"))))
        Layout.render(doc, style) shouldBe "a b"
    }

    should("render a Group broken when its flat content would exceed maxLineLength") {
        val doc = Doc.Group(
            Doc.Concat(
                listOf(Doc.Text("a very long first token"), Doc.Break(BreakKind.SOFT), Doc.Text("and a second one")),
            ),
        )
        Layout.render(doc, style) shouldBe "a very long first token\nand a second one"
    }

    fun arguments(
        vararg items: Doc,
        forceBreak: Boolean = false,
        singleArgument: Boolean = false,
        forceNestedWhenBroken: Boolean = false,
    ): Doc.Group {
        val interior = ArrayList<Doc>()
        interior.add(Doc.Break(BreakKind.SOFT, flat = ""))
        for ((i, item) in items.withIndex()) {
            if (i > 0) {
                interior.add(Doc.Text(","))
                interior.add(Doc.Break(BreakKind.SOFT, flat = " "))
            }
            interior.add(item)
        }
        val body = Doc.Concat(
            listOf(
                Doc.Text("("),
                Doc.Indent(Doc.Concat(interior)),
                Doc.Break(BreakKind.SOFT, flat = ""),
                Doc.Text(")"),
            ),
        )
        return Doc.Group(
            body,
            GroupKind.ARGUMENTS,
            forceBreak = forceBreak,
            singleArgument = singleArgument,
            forceNestedWhenBroken = forceNestedWhenBroken,
        )
    }

    should("a single-argument list that nests a call forces the nested lists only once it breaks by width") {
        val nested = Doc.Concat(listOf(Doc.Text("k"), arguments(Doc.Text("y"), Doc.Text("z"))))
        val fits = Doc.Concat(
            listOf(Doc.Text("g"), arguments(nested, singleArgument = true, forceNestedWhenBroken = true)),
        )
        Layout.render(fits, style) shouldBe "g(k(y, z))"
        val wide = Doc.Concat(
            listOf(
                Doc.Text("longer.receiver.g"),
                arguments(nested, singleArgument = true, forceNestedWhenBroken = true),
            ),
        )
        Layout.render(wide, style) shouldBe "longer.receiver.g(\n    k(\n        y,\n        z\n    )\n)"
    }

    should("a forced ARGUMENTS group breaks every nested argument list but leaves a single-argument one flat") {
        val inner = Doc.Concat(listOf(Doc.Text("g"), arguments(Doc.Text("x"), singleArgument = true)))
        val other = Doc.Concat(listOf(Doc.Text("h"), arguments(Doc.Text("y"), Doc.Text("z"))))
        val doc = Doc.Concat(listOf(Doc.Text("f"), arguments(inner, other, forceBreak = true)))
        Layout.render(doc, style) shouldBe "f(\n    g(x),\n    h(\n        y,\n        z\n    )\n)"
    }

    should("a single-argument list passes no forcing on to the lists nested in it") {
        val deepest = Doc.Concat(listOf(Doc.Text("k"), arguments(Doc.Text("y"), Doc.Text("z"))))
        val inner = Doc.Concat(listOf(Doc.Text("g"), arguments(deepest, singleArgument = true)))
        val doc = Doc.Concat(listOf(Doc.Text("f"), arguments(inner, Doc.Text("w"), forceBreak = true)))
        Layout.render(doc, style) shouldBe "f(\n    g(k(y, z)),\n    w\n)"
    }

    should("a BARRIER group stops the forcing and renders in the enclosing mode") {
        val condition = Doc.Concat(listOf(Doc.Text("if (c"), arguments(Doc.Text("y"), Doc.Text("z")), Doc.Text(") a")))
        val barrier = Doc.Group(condition, GroupKind.BARRIER)
        val doc = Doc.Concat(listOf(Doc.Text("f"), arguments(barrier, Doc.Text("w"), forceBreak = true)))
        Layout.render(doc, style) shouldBe "f(\n    if (c(y, z)) a,\n    w\n)"
    }

    should("no group renders flat around a forced ARGUMENTS group") {
        val forced = Doc.Concat(listOf(Doc.Text("f"), arguments(Doc.Text("y"), Doc.Text("z"), forceBreak = true)))
        val outer = Doc.Group(
            Doc.Concat(listOf(Doc.Text("x ="), Doc.Break(BreakKind.SOFT), forced)),
            indentWhenBroken = true,
        )
        Layout.render(outer, style) shouldBe "x =\n    f(\n        y,\n        z\n    )"
    }

    should("force a Group broken when it contains a HARD break regardless of width") {
        val doc = Doc.Group(Doc.Concat(listOf(Doc.Text("a"), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("b"))))
        Layout.render(doc, style) shouldBe "a\nb"
    }

    should("FLUID group joins the operator line when the value's first line fits, leaving the nested group to break") {
        val args = Doc.Group(
            Doc.Concat(
                listOf(
                    Doc.Text("("),
                    Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("argument-one")))),
                    Doc.Break(BreakKind.SOFT, flat = ""),
                    Doc.Text(")"),
                ),
            ),
        )
        val value = Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), Doc.Text("call"), args))
        val doc = Doc.Concat(listOf(Doc.Text("val x ="), Doc.Group(value, GroupKind.FLUID, indentWhenBroken = true)))
        Layout.render(doc, style) shouldBe "val x = call(\n    argument-one\n)"
    }

    should("FLUID group breaks after the operator and indents the value when its first line does not fit") {
        val value = Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), Doc.Text("long.receiver()")))
        val doc = Doc.Concat(listOf(Doc.Text("val x ="), Doc.Group(value, GroupKind.FLUID, indentWhenBroken = true)))
        Layout.render(doc, style) shouldBe "val x =\n    long.receiver()"
    }

    should("FLUID group measures up to a HARD break inside the value") {
        val lambda = Doc.Concat(
            listOf(
                Doc.Text("run {"),
                Doc.Indent(
                    Doc.Concat(
                        listOf(
                            Doc.Break(BreakKind.HARD, literal = "\n"),
                            Doc.Text("a very long body line that never counts"),
                        ),
                    ),
                ),
                Doc.Break(BreakKind.HARD, literal = "\n"),
                Doc.Text("}"),
            ),
        )
        val doc = Doc.Concat(
            listOf(
                Doc.Text("val x ="),
                Doc.Group(
                    Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), lambda)),
                    GroupKind.FLUID,
                    indentWhenBroken = true,
                ),
            ),
        )
        Layout.render(doc, style) shouldBe "val x = run {\n    a very long body line that never counts\n}"
    }

    should("a tail stops at a HARD break but counts the content before it") {
        val args = Doc.Group(
            Doc.Concat(
                listOf(
                    Doc.Text("("),
                    Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a")))),
                    Doc.Break(BreakKind.SOFT, flat = ""),
                    Doc.Text(")"),
                ),
            ),
        )
        val lambda = Doc.Concat(
            listOf(
                Doc.Text(" { x, y, z, w ->"),
                Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("x")))),
                Doc.Break(BreakKind.HARD, literal = "\n"),
                Doc.Text("}"),
            ),
        )
        val doc = Doc.Concat(listOf(Doc.Text("fold"), args, lambda))
        Layout.render(doc, style) shouldBe "fold(\n    a\n) { x, y, z, w ->\n    x\n}"
    }

    should("a DEFAULT group in tail position counts its whole flat width, so the preceding group breaks first") {
        val params = Doc.Group(
            Doc.Concat(
                listOf(
                    Doc.Text("("),
                    Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a: A")))),
                    Doc.Break(BreakKind.SOFT, flat = ""),
                    Doc.Text(")"),
                ),
            ),
        )
        val args = Doc.Group(
            Doc.Concat(
                listOf(
                    Doc.Text("("),
                    Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a")))),
                    Doc.Break(BreakKind.SOFT, flat = ""),
                    Doc.Text(")"),
                ),
            ),
        )
        val doc = Doc.Concat(listOf(Doc.Text("fun f"), params, Doc.Text(" = call"), args))
        Layout.render(doc, style) shouldBe "fun f(\n    a: A\n) = call(a)"
    }

    should(
        "a forced ARGUMENTS group breaks and forces argument lists nested through FLUID groups, not through a lambda",
    ) {
        fun args(
            vararg items: Doc,
            force: Boolean = false,
        ): Doc {
            val interior = ArrayList<Doc>()
            interior.add(Doc.Break(BreakKind.SOFT, flat = ""))
            for ((i, item) in items.withIndex()) {
                if (i > 0) {
                    interior.add(Doc.Text(","))
                    interior.add(Doc.Break(BreakKind.SOFT))
                }
                interior.add(item)
            }
            interior.add(Doc.TrailingComma())
            return Doc.Group(
                Doc.Concat(
                    listOf(
                        Doc.Text("("),
                        Doc.Indent(Doc.Concat(interior)),
                        Doc.Break(BreakKind.SOFT, flat = ""),
                        Doc.Text(")"),
                    ),
                ),
                GroupKind.ARGUMENTS,
                forceBreak = force,
            )
        }
        val leaf = Doc.Concat(listOf(Doc.Text("b"), args(Doc.Text("1"))))
        val named = Doc.Concat(
            listOf(
                Doc.Text("n ="),
                Doc.Group(
                    Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), leaf)),
                    GroupKind.FLUID,
                    indentWhenBroken = true,
                ),
            ),
        )
        val lambda = Doc.Group(
            Doc.Concat(listOf(Doc.Text("{ "), Doc.Text("c"), args(Doc.Text("2")), Doc.Text(" }"))),
            GroupKind.LAMBDA,
        )
        val doc = Doc.Concat(listOf(Doc.Text("a"), args(named, lambda, force = true)))
        Layout.render(
            doc,
            FormatStyle(indentWidth = 4, maxLineLength = 80),
        ) shouldBe "a(\n    n = b(\n        1,\n    ),\n    { c(2) },\n)"
    }

    should("decide nested groups independently once the outer group's mode is known") {
        val inner = Doc.Group(Doc.Concat(listOf(Doc.Text("inner-a"), Doc.Break(BreakKind.SOFT), Doc.Text("inner-b"))))
        val outer = Doc.Group(
            Doc.Concat(listOf(Doc.Text("outer-prefix-that-is-long"), Doc.Break(BreakKind.SOFT), inner)),
        )
        Layout.render(outer, style) shouldBe "outer-prefix-that-is-long\ninner-a inner-b"
    }

    should("force a Group broken when it contains a Text leaf with an embedded newline") {
        val doc = Doc.Group(
            Doc.Concat(listOf(Doc.Text("\"\"\"\nraw\n\"\"\""), Doc.Break(BreakKind.SOFT), Doc.Text("tail"))),
        )
        Layout.render(doc, style) shouldBe "\"\"\"\nraw\n\"\"\"\ntail"
    }

    should("resume column counting from after a Text leaf's last embedded newline, not its total length") {
        val doc = Doc.Concat(
            listOf(
                Doc.Text("aaaaaaaaaaaaaaaaaaaa\nbb"),
                Doc.Group(Doc.Concat(listOf(Doc.Text("cc"), Doc.Break(BreakKind.SOFT), Doc.Text("dd")))),
            ),
        )
        Layout.render(doc, style) shouldBe "aaaaaaaaaaaaaaaaaaaa\nbbcc dd"
    }
})
