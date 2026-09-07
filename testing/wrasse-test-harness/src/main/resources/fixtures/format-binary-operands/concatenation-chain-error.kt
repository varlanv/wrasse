package sample

const val MESSAGE = "The declaration should only be used when a method stub is necessary, " + "this defers the development of the functionality of this function, " + "hence the declaration should only serve as a temporary declaration"

val greeting = "hello, " +
    "world"

// expect-error 1:1 format "File is not wrasse-formatted"
