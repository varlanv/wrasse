package sample

class Box(val value :Int) {
    val doubled :Int = value * 2

    fun describe() :String {
        return "value=$value"
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
