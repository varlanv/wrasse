package io.kotest.matchers.nulls

fun <T> T?.shouldBeNull(): T? = this

fun <T : Any> T?.shouldNotBeNull(): T = this!!
