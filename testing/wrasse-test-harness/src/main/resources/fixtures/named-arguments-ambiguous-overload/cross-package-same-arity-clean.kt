package sample

import sample.pkga.render
import sample.pkgb.render

fun demo(): Int = render(width = 5)

// fixture-aux-file: aux/RenderA.kt
// fixture-aux-file: aux/RenderB.kt
// expect-clean
