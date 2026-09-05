package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LayoutSpec :
    BaseSpec(
        {

            val style = FormatStyle(indentWidth = 4, maxLineLength = 20)

            should("render Text verbatim") {
                Layout.render(Doc.Text("val x = 1"), style) shouldBe "val x = 1"
            }

            should("render a HARD break as its literal plus the ambient indent") {
                val doc = Doc
                    .Concat(
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
                val doc = Doc
                    .Group(Doc.Concat(listOf(Doc.Text("a very long first token"), Doc.Break(BreakKind.SOFT), Doc.Text("and a second one"))))
                Layout.render(doc, style) shouldBe "a very long first token\nand a second one"
            }

            should("force a Group broken when it contains a HARD break regardless of width") {
                val doc = Doc.Group(Doc.Concat(listOf(Doc.Text("a"), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("b"))))
                Layout.render(doc, style) shouldBe "a\nb"
            }

            should("FLUID group joins the operator line when the value's first line fits, leaving the nested group to break") {
                val args = Doc.Group(Doc.Concat(listOf(Doc.Text("("), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("argument-one")))), Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text(")"))))
                val value = Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), Doc.Text("call"), args))
                val doc = Doc.Concat(listOf(Doc.Text("val x ="), Doc.Group(value, GroupKind.FLUID)))
                Layout.render(doc, style) shouldBe "val x = call(\n    argument-one\n)"
            }

            should("FLUID group breaks after the operator and indents the value when its first line does not fit") {
                val value = Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), Doc.Text("long.receiver()")))
                val doc = Doc.Concat(listOf(Doc.Text("val x ="), Doc.Group(value, GroupKind.FLUID)))
                Layout.render(doc, style) shouldBe "val x =\n    long.receiver()"
            }

            should("FLUID group measures up to a HARD break inside the value") {
                val lambda = Doc.Concat(listOf(Doc.Text("run {"), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("a very long body line that never counts")))), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("}")))
                val doc = Doc.Concat(listOf(Doc.Text("val x ="), Doc.Group(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT), lambda)), GroupKind.FLUID)))
                Layout.render(doc, style) shouldBe "val x = run {\n    a very long body line that never counts\n}"
            }

            should("a tail stops at a HARD break but counts the content before it") {
                val args = Doc.Group(Doc.Concat(listOf(Doc.Text("("), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a")))), Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text(")"))))
                val lambda = Doc.Concat(listOf(Doc.Text(" { x, y, z, w ->"), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("x")))), Doc.Break(BreakKind.HARD, literal = "\n"), Doc.Text("}")))
                val doc = Doc.Concat(listOf(Doc.Text("fold"), args, lambda))
                Layout.render(doc, style) shouldBe "fold(\n    a\n) { x, y, z, w ->\n    x\n}"
            }

            should("a DEFAULT group in tail position counts its whole flat width, so the preceding group breaks first") {
                val params = Doc.Group(Doc.Concat(listOf(Doc.Text("("), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a: A")))), Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text(")"))))
                val args = Doc.Group(Doc.Concat(listOf(Doc.Text("("), Doc.Indent(Doc.Concat(listOf(Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text("a")))), Doc.Break(BreakKind.SOFT, flat = ""), Doc.Text(")"))))
                val doc = Doc.Concat(listOf(Doc.Text("fun f"), params, Doc.Text(" = call"), args))
                Layout.render(doc, style) shouldBe "fun f(\n    a: A\n) = call(a)"
            }

            should("decide nested groups independently once the outer group's mode is known") {
                val inner = Doc.Group(Doc.Concat(listOf(Doc.Text("inner-a"), Doc.Break(BreakKind.SOFT), Doc.Text("inner-b"))))
                val outer = Doc.Group(Doc.Concat(listOf(Doc.Text("outer-prefix-that-is-long"), Doc.Break(BreakKind.SOFT), inner)))
                Layout.render(outer, style) shouldBe "outer-prefix-that-is-long\ninner-a inner-b"
            }

            should("force a Group broken when it contains a Text leaf with an embedded newline") {
                val doc = Doc.Group(Doc.Concat(listOf(Doc.Text("\"\"\"\nraw\n\"\"\""), Doc.Break(BreakKind.SOFT), Doc.Text("tail"))))
                Layout.render(doc, style) shouldBe "\"\"\"\nraw\n\"\"\"\ntail"
            }

            should("resume column counting from after a Text leaf's last embedded newline, not its total length") {
                val doc = Doc
                    .Concat(
                        listOf(
                            Doc.Text("aaaaaaaaaaaaaaaaaaaa\nbb"),
                            Doc.Group(Doc.Concat(listOf(Doc.Text("cc"), Doc.Break(BreakKind.SOFT), Doc.Text("dd")))),
                        ),
                    )
                Layout.render(doc, style) shouldBe "aaaaaaaaaaaaaaaaaaaa\nbbcc dd"
            }
        },
    )
