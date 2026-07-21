package sample

fun foo() {
    if (false) {

        println(1)
    } else if (true) {

        println(2)
    } else {

        println(3)
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
