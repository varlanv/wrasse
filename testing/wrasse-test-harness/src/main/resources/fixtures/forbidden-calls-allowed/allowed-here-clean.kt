package sample

import sample.clock.Ticker
import sample.clock.now

fun stamp(): Long = now() + Ticker(1).tick()

// fixture-aux-file: aux/Clock.kt
// expect-clean
