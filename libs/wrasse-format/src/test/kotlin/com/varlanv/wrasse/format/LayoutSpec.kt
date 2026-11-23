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
        Layout.render(doc, FormatStyle(indentWidth = 4, maxLineLength = 80)) shouldBe
            "a(\n    n = b(\n        1,\n    ),\n    { c(2) },\n)"
    }

    fun fill(vararg items: String): Doc.Group {
        val interior = ArrayList<Doc>()
        for ((i, item) in items.withIndex()) {
            if (i > 0) {
                interior.add(Doc.Text(","))
                interior.add(Doc.Break(BreakKind.SOFT, flat = " "))
            }
            interior.add(Doc.Text(item))
        }
        return Doc.Group(Doc.Concat(interior), GroupKind.FILL)
    }

    should("render a FILL group flat while its segments fit") {
        Layout.render(fill("aa", "bb"), style) shouldBe "aa, bb"
    }

    should("break a FILL group only before the segment that would overflow, packing the rest") {
        Layout.render(fill("aaa", "bbb", "ccc", "ddd", "eee"), style) shouldBe "aaa, bbb, ccc, ddd,\neee"
    }

    should("measure a FILL group's last segment together with the tail, moving both down") {
        val doc = Doc.Concat(listOf(fill("aaa", "bbb", "ccc", "ddd"), Doc.Text(" -> x")))
        Layout.render(doc, style) shouldBe "aaa, bbb, ccc,\nddd -> x"
    }

    should("start a FILL segment that has no flat form of its own on its own line") {
        val multiline = Doc.Group(
            Doc.Concat(listOf(Doc.Text("m("), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text(")"))),
        )
        val doc = Doc.Group(
            Doc.Concat(listOf(Doc.Text("aa"), Doc.Text(","), Doc.Break(BreakKind.SOFT, flat = " "), multiline)),
            GroupKind.FILL,
        )
        Layout.render(doc, style) shouldBe "aa,\nm(\n)"
    }

    fun packedArguments(
        vararg items: String,
        trailingComma: Boolean = true,
        forceBreak: Boolean = false,
    ): Doc.Group {
        val elements = ArrayList<Doc>()
        for ((i, item) in items.withIndex()) {
            if (i > 0) {
                elements.add(Doc.Text(","))
                elements.add(Doc.Break(BreakKind.SOFT, flat = " "))
            }
            elements.add(Doc.Text(item))
        }
        if (trailingComma) elements.add(Doc.TrailingComma())
        val interior = Doc.Concat(
            listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Group(Doc.Concat(elements), GroupKind.PACKED)),
        )
        val body = Doc.Concat(
            listOf(Doc.Text("("), Doc.Indent(interior), Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text(")")),
        )
        return Doc.Group(body, GroupKind.ARGUMENTS, forceBreak = forceBreak)
    }

    fun call(name: String, arguments: Doc): Doc = Doc.Concat(listOf(Doc.Text(name), arguments))

    should("render a PACKED argument list flat, without a trailing comma, while the list fits") {
        Layout.render(call("f", packedArguments("aaaa", "bbbb", "cccc")), style) shouldBe "f(aaaa, bbbb, cccc)"
    }

    should("fill a broken PACKED argument list line by line with the trailing comma after the last element") {
        val doc = call("fffff", packedArguments("aaaa", "bbbb", "cccc", "dddd", "eeee"))
        Layout.render(doc, style) shouldBe "fffff(\n    aaaa, bbbb,\n    cccc, dddd,\n    eeee,\n)"
    }

    should("keep an element and its comma on a line that ends exactly at the width limit") {
        val doc = call("fffff", packedArguments("aaaa", "bbbb", "ccc", "d"))
        Layout.render(doc, style) shouldBe "fffff(\n    aaaa, bbbb, ccc,\n    d,\n)"
    }

    should("move an element to the next line when its comma would end one past the width limit") {
        val doc = call("fffff", packedArguments("aaaa", "bbbb", "cccc", "d"))
        Layout.render(doc, style) shouldBe "fffff(\n    aaaa, bbbb,\n    cccc, d,\n)"
    }

    should("count the trailing comma towards the last element's width only when it is rendered") {
        Layout.render(call("fffff", packedArguments("aaaa", "bbbb", "cccc")), style) shouldBe
            "fffff(\n    aaaa, bbbb,\n    cccc,\n)"
        Layout.render(call("fffff", packedArguments("aaaa", "bbbb", "cccc", trailingComma = false)), style) shouldBe
            "fffff(\n    aaaa, bbbb, cccc\n)"
    }

    should("give an element wider than the limit a line of its own without splitting it") {
        val doc = call("f", packedArguments("aa", "bbbbbbbbbbbbbbbbbbbbbbbb", "cc", "dd", "ee", "ff", "gg"))
        Layout.render(doc, style) shouldBe "f(\n    aa,\n    bbbbbbbbbbbbbbbbbbbbbbbb,\n    cc, dd, ee, ff,\n    gg,\n)"
    }

    should("fill from the indent of the context the argument list stands in") {
        val top = call("f", packedArguments("aaaaa", "bbbbb", "ccccc"))
        Layout.render(top, style) shouldBe "f(\n    aaaaa, bbbbb,\n    ccccc,\n)"
        val nested = Doc.Concat(
            listOf(Doc.Text("x"), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), top)))),
        )
        Layout.render(nested, style) shouldBe "x\n    f(\n        aaaaa,\n        bbbbb,\n        ccccc,\n    )"
    }

    should("render a forced PACKED argument list one element per line") {
        val doc = call("f", packedArguments("aa", "bb", forceBreak = true))
        Layout.render(doc, style) shouldBe "f(\n    aa,\n    bb,\n)"
    }

    should("render a PACKED argument list nested in a forced argument list one element per line") {
        val inner = call("g", packedArguments("aa", "bb"))
        val doc = call("f", arguments(inner, Doc.Text("cc"), forceBreak = true))
        Layout.render(doc, style) shouldBe "f(\n    g(\n        aa,\n        bb,\n    ),\n    cc\n)"
    }

    should("fill a PACKED argument list nested in an argument list that is merely broken by width") {
        val inner = call("g", packedArguments("aaaa", "bbbb", "cccc"))
        val doc = call("f", arguments(inner, Doc.Text("dd")))
        Layout.render(doc, style) shouldBe "f(\n    g(\n        aaaa, bbbb,\n        cccc,\n    ),\n    dd\n)"
    }

    fun matrixCell(
        text: String,
        last: Boolean = false,
    ): Doc = Doc.Concat(listOf(Doc.Text(text), if (last) Doc.TrailingComma() else Doc.Text(",")))

    fun matrix(
        columns: Int,
        vararg items: String,
        trailingComma: Boolean = true,
    ): Doc {
        val parts = ArrayList<Doc>()
        for ((index, item) in items.withIndex()) {
            if (index > 0) parts.add(Doc.Break(BreakKind.SOFT, flat = " "))
            parts.add(
                if (index == items.lastIndex && trailingComma) {
                    matrixCell(item, last = true)
                } else if (index == items.lastIndex) {
                    Doc.Text(item)
                } else {
                    matrixCell(item)
                },
            )
        }
        val body = Doc.Concat(
            listOf(
                Doc.Break(BreakKind.HARD, literal = "\n"),
                Doc.Group(Doc.Concat(parts), GroupKind.MATRIX, columns = columns),
            ),
        )
        return Doc.Concat(
            listOf(Doc.Text("f("), Doc.Indent(body), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text(")")),
        )
    }

    should("render a MATRIX group in rows of its column count") {
        Layout.render(matrix(2, "1", "2", "3", "4"), style) shouldBe "f(\n    1, 2,\n    3, 4,\n)"
    }

    should("render a MATRIX group of one row when the column count equals the cell count") {
        Layout.render(matrix(3, "1", "2", "3"), style) shouldBe "f(\n    1, 2, 3,\n)"
    }

    should("pad every column but the last to its widest cell and leave no trailing whitespace") {
        val doc = matrix(3, "1", "100", "7", "250", "3", "40")
        Layout.render(doc, style) shouldBe "f(\n    1,   100, 7,\n    250, 3,   40,\n)"
    }

    should("align each column on its own widest cell") {
        val doc = matrix(2, "a", "b", "ccccc", "d", "ee", "f")
        Layout.render(doc, style) shouldBe "f(\n    a,     b,\n    ccccc, d,\n    ee,    f,\n)"
    }

    should("render a MATRIX group wider than the line limit without breaking inside a row") {
        val wide = "\"" + "w".repeat(30) + "\""
        val doc = matrix(2, wide, wide, "a", "b")
        Layout.render(doc, style) shouldBe "f(\n    $wide, $wide,\n    a," + " ".repeat(wide.length - 1) + " b,\n)"
    }

    should("measure a cell by its UTF-16 length") {
        val doc = matrix(2, "\"\uD83D\uDE00\"", "a", "\"abc\"", "b")
        Layout.render(doc, style) shouldBe "f(\n    \"\uD83D\uDE00\",  a,\n    \"abc\", b,\n)"
    }

    should("render a MATRIX cell flat whatever groups and breaks it holds") {
        val element = Doc.Group(
            Doc.Concat(
                listOf(
                    Doc.Text("Vec("),
                    Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("1, 2")))),
                    Doc.Break(BreakKind.SOFT, flat = ""),
                    Doc.Text(")"),
                ),
            ),
            GroupKind.ARGUMENTS,
            forceBreak = true,
        )
        val parts = listOf(
            Doc.Concat(listOf(element, Doc.Text(","))),
            Doc.Break(BreakKind.SOFT, flat = " "),
            Doc.Concat(listOf(Doc.Text("x"), Doc.TrailingComma())),
        )
        val doc = Doc.Group(Doc.Concat(parts), GroupKind.MATRIX, columns = 2)

        Layout.render(doc, style) shouldBe "Vec(1, 2), x,"
    }

    should("omit the trailing comma of a MATRIX group that has none") {
        Layout.render(matrix(2, "1", "2", "3", "4", trailingComma = false), style) shouldBe "f(\n    1, 2,\n    3, 4\n)"
    }

    should("lay a MATRIX group out from the indent it stands at") {
        val nested = Doc.Concat(
            listOf(
                Doc.Text("x"),
                Doc.Indent(
                    Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), matrix(2, "1", "22", "333", "4"))),
                ),
            ),
        )
        Layout.render(nested, style) shouldBe "x\n    f(\n        1,   22,\n        333, 4,\n    )"
    }

    should("never fit a group holding a MATRIX group on one line") {
        val doc = Doc.Group(Doc.Concat(listOf(Doc.Text("a"), Doc.Break(BreakKind.SOFT), matrix(2, "1", "2"))))
        Layout.render(doc, style) shouldBe "a\nf(\n    1, 2,\n)"
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
