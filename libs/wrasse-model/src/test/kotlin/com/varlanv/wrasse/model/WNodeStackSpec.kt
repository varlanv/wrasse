package com.varlanv.wrasse.model

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class WNodeStackSpec :
    BaseSpec(
        {

            should("track contains and peek across push/pop") {
                val stack = WNodeStack()
                stack.push(WNodeType.CLASS, 0, 10)
                stack.push(WNodeType.CLASS_BODY, 1, 9)

                stack.contains(WNodeType.CLASS) shouldBe true
                stack.contains(WNodeType.CLASS_BODY) shouldBe true
                stack.contains(WNodeType.FUN) shouldBe false
                stack.peekType() shouldBe WNodeType.CLASS_BODY

                stack.pop()
                stack.contains(WNodeType.CLASS_BODY) shouldBe false
                stack.contains(WNodeType.CLASS) shouldBe true
                stack.peekType() shouldBe WNodeType.CLASS
            }

            should("reset contains for previously-pushed types after clear") {
                val stack = WNodeStack()
                stack.push(WNodeType.CLASS, 0, 10)
                stack.push(WNodeType.CLASS_BODY, 1, 9)
                stack.push(WNodeType.FUN, 2, 8)

                stack.clear()

                stack.isEmpty shouldBe true
                stack.contains(WNodeType.CLASS) shouldBe false
                stack.contains(WNodeType.CLASS_BODY) shouldBe false
                stack.contains(WNodeType.FUN) shouldBe false
            }

            should("behave correctly for a fresh push sequence reusing an instance after clear") {
                val stack = WNodeStack()
                stack.push(WNodeType.CLASS, 0, 10)
                stack.push(WNodeType.CLASS_BODY, 1, 9)
                stack.clear()

                stack.push(WNodeType.FUN, 0, 5)
                stack.push(WNodeType.BLOCK, 1, 4)

                stack.contains(WNodeType.FUN) shouldBe true
                stack.contains(WNodeType.BLOCK) shouldBe true
                stack.contains(WNodeType.CLASS) shouldBe false
                stack.contains(WNodeType.CLASS_BODY) shouldBe false
                stack.peekType() shouldBe WNodeType.BLOCK
                stack.size shouldBe 2
            }

            should("reset counts for repeated same-type ancestors after clear") {
                val stack = WNodeStack()
                stack.push(WNodeType.CLASS, 0, 10)
                stack.push(WNodeType.CLASS, 1, 9)
                stack.push(WNodeType.CLASS, 2, 8)

                stack.clear()

                stack.contains(WNodeType.CLASS) shouldBe false
            }
        },
    )
