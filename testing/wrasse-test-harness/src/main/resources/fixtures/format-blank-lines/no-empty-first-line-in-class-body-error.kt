package sample

class Outer {

    class Inner {

        val value = 1
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
