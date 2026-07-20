package sample

fun branch(flag: Boolean) {

    if (flag) {

        println("yes")
    } else {

        println("no")
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
