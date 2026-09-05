package sample

import sample.clock.now

@Suppress("forbidden-calls")
fun stamp(): Long = now()

// fixture-aux-file: aux/Clock.kt
// expect-clean
