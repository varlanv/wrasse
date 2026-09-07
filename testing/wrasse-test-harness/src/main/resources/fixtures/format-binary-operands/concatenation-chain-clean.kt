package sample

const val MESSAGE = "The declaration should only be used when a method stub is necessary, " +
    "this defers the development of the functionality of this function, " +
    "hence the declaration should only serve as a temporary declaration"

fun describe(count: Int, total: Int): String {
    return "processed " + count + " of " + total
}

// expect-clean
