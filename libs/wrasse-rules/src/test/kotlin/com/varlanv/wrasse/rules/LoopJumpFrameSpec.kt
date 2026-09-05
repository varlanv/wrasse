package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LoopJumpFrameSpec : BaseSpec({

    should("start at zero jumps") {
        val frame = LoopJumpFrame(0, 3)

        frame.jumpCount shouldBe 0
    }

    should("accumulate one recorded jump per call") {
        val frame = LoopJumpFrame(0, 3)

        frame.recordJump()
        frame.recordJump()
        frame.recordJump()

        frame.jumpCount shouldBe 3
    }

    should("keep its own offsets untouched by recording") {
        val frame = LoopJumpFrame(10, 13)

        frame.recordJump()

        frame.loopStart shouldBe 10
        frame.loopEnd shouldBe 13
    }
})
