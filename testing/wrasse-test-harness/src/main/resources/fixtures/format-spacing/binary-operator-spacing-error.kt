package sample

fun demo(a: Int, b: Int): Int {
    val c: Int? = a
    val sum = a  +  b
    val diff = a -  b
    val ok = a  ==  b
    var counter = a
    counter  +=  b
    val fallback = c  ?:  b
    return if (ok) sum else diff + counter + fallback
}

// expect-error 1:1 format "File is not wrasse-formatted"
