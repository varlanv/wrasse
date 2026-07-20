package sample

fun demo(numbers: List<Int>) {
    for(n in numbers) {
        while(n > 0) {
            break
        }
    }
    when(numbers.size) {
        0 -> println("empty")
        else -> println("non-empty")
    }
    try {
        println("ok")
    } catch(e: Exception) {
        println(e.message)
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
