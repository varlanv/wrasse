package sample

import sample.clock.Ticker
import sample.clock.now

fun demo(items: List<String>): Long {
    val ticker = Ticker(1)
    val byLength = items.associateBy { it.length }
    val withLength = items.associateWith { it.length }
    val fine = ticker.tick()
    return now() + ticker.tickBy(2) + byLength.size + withLength.size + fine
}

// fixture-aux-file: aux/Clock.kt
// expect-error 7:18 forbidden-calls "Call to 'sample.clock.Ticker' is forbidden"
// expect-error 8:26 forbidden-calls "Call to 'kotlin.collections.associateBy' is forbidden"
// expect-error 9:28 forbidden-calls "Call to 'kotlin.collections.associateWith' is forbidden"
// expect-error 11:12 forbidden-calls "Call to 'sample.clock.now' is forbidden"
// expect-error 11:27 forbidden-calls "Call to 'sample.clock.Ticker.tickBy' is forbidden"
