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
