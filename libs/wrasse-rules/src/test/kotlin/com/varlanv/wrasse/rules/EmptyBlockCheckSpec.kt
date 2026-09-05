package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class EmptyBlockCheckSpec : BaseSpec({

    should("treat a block with only braces and whitespace as empty") {
        val children = ChildBuffer()
        children.add(WNodeType.LBRACE, 0, 1, null)
        children.add(WNodeType.WHITE_SPACE, 1, 2, " ")
        children.add(WNodeType.RBRACE, 2, 3, null)

        EmptyBlockCheck.isEmpty(children) shouldBe true
    }

    should("treat a block containing a statement as non-empty") {
        val children = ChildBuffer()
        children.add(WNodeType.LBRACE, 0, 1, null)
        children.add(WNodeType.CALL_EXPRESSION, 1, 10, null)
        children.add(WNodeType.RBRACE, 10, 11, null)

        EmptyBlockCheck.isEmpty(children) shouldBe false
    }

    should("treat a block containing only a comment as non-empty") {
        val children = ChildBuffer()
        children.add(WNodeType.LBRACE, 0, 1, null)
        children.add(WNodeType.EOL_COMMENT, 1, 10, "// no-op")
        children.add(WNodeType.RBRACE, 10, 11, null)

        EmptyBlockCheck.isEmpty(children) shouldBe false
    }

    should("read emptiness directly from a block's own span text") {
        EmptyBlockCheck.isEmptySpan("fun f() {  }", 8, 12) shouldBe true
        EmptyBlockCheck.isEmptySpan("fun f() { foo() }", 8, 17) shouldBe false
        EmptyBlockCheck.isEmptySpan("fun f() { /* x */ }", 8, 19) shouldBe false
    }
})
