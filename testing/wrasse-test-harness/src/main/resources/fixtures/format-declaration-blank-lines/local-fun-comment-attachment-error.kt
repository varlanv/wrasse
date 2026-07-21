package sample

class Holder {
    fun test() {
        // comment 1
        fun bar1() = 1
        /*
         * comment 2
         */
        fun bar2() = 2
        println(bar1() + bar2())
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
