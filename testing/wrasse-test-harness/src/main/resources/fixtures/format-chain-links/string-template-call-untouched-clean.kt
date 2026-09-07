package sample

fun render(prefix: String, values: List<String>): List<String> = values.map { value ->
    "${prefix.uppercase()}-${value.trim().lowercase()}: ${values.indexOf(value)}"
}

// expect-clean
