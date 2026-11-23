package sample

fun main() {
    listOf(1, 2).forEach { item ->
        println( item )
        // wrasse fmt:off
        println(   item   )
          println(item   )
        // wrasse fmt:on
        println(   item   )
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
