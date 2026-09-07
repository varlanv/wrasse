package io.kotest.matchers.collections

infix fun <T> Collection<T>.shouldHaveSize(size: Int): Collection<T> = this

fun <T> Collection<T>.shouldBeEmpty(): Collection<T> = this
