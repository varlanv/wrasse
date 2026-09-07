package io.kotest.assertions

fun <R> withClue(clue: Any?, thunk: () -> R): R = thunk()
