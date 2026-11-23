package sample

class Lookup {
    fun resolve(a: String, b: String) = a + b
}

fun describe(lookup: Lookup): String {
    return "label: ${lookup.resolve("alpha-key", "beta-key")} suffix"
}

// expect-clean
