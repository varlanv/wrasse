package sample

class Box<T:Any>(val value :T) {
    fun get() :T {
        return value
    }
}

fun demo(numbers: List<Int>, threshold: Int) {
    if(numbers.isEmpty()) {
        println("empty")
    } else {
        val doubled = numbers.map {n -> n * 2}
        println(doubled[ 0 ])
    }
    for(n in 1 .. threshold) {
        val negated = - n
        println(negated)
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
