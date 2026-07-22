package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class KdocTagMismatchDecisionSpec :
    BaseSpec(
        {

            should("not report when the KDoc has no param/property tags at all") {
                KdocTagMismatchDecision.decide(emptyList(), listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM))).shouldBeNull()
            }

            should("not report a fully matching, correctly ordered doc") {
                val doc = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM), KdocDeclaration("bar", KdocDeclarationKind.PROPERTY))
                val element = listOf(
                    KdocDeclaration("foo", KdocDeclarationKind.PARAM),
                    KdocDeclaration("bar", KdocDeclarationKind.PROPERTY),
                )
                KdocTagMismatchDecision.decide(doc, element).shouldBeNull()
            }

            should("report a documented name absent from the declaration") {
                val doc = listOf(KdocDeclaration("wrong", KdocDeclarationKind.PARAM))
                val element = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM))
                KdocTagMismatchDecision.decide(doc, element) shouldBe "documented parameters 'wrong' are not present in the declaration"
            }

            should("report a param tag used for a constructor property by default") {
                val doc = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM))
                val element = listOf(KdocDeclaration("foo", KdocDeclarationKind.PROPERTY))
                KdocTagMismatchDecision.decide(doc, element) shouldBe "documented parameters 'foo' are not present in the declaration"
            }

            should("report documentation order not matching declaration order") {
                val doc = listOf(KdocDeclaration("bar", KdocDeclarationKind.PARAM), KdocDeclaration("foo", KdocDeclarationKind.PARAM))
                val element = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM), KdocDeclaration("bar", KdocDeclarationKind.PARAM))
                KdocTagMismatchDecision.decide(doc, element) shouldBe "order of documented parameters does not match the declaration order"
            }

            should("report an undocumented declared parameter when the doc is non-empty but incomplete") {
                val doc = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM))
                val element = listOf(KdocDeclaration("foo", KdocDeclarationKind.PARAM), KdocDeclaration("bar", KdocDeclarationKind.PARAM))
                KdocTagMismatchDecision.decide(doc, element) shouldBe "parameters 'bar' are not documented"
            }
        },
    )
