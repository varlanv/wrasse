package com.varlanv.wrasse.lang

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.concurrency.TestExecutionMode

abstract class BaseSpec(
    body: ShouldSpec.() -> Unit,
) : ShouldSpec({
        isolationMode = IsolationMode.SingleInstance
        testExecutionMode = TestExecutionMode.LimitedConcurrency(Runtime.getRuntime().availableProcessors())
        body()
    })
