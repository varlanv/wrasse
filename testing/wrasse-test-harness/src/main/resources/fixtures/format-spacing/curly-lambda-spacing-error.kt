package sample

fun demo(names: List<String>) {
    names.forEach {name -> println(name)}
    names.forEach {}
}

// expect-error 1:1 format "File is not wrasse-formatted"
