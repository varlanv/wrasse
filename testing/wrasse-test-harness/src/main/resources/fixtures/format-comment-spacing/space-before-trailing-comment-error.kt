package sample

fun greet(name: String): String {
    val greeting = "Hello, $name"// no gap at all before comment
    return greeting
}

// expect-error 1:1 format "File is not wrasse-formatted"
