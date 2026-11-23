package sample

class Box {
    fun  show( ) {
        if (true) {
                // wrasse fmt:off
            val  a  =  1
      // wrasse fmt:on
            val  b  =  2
        }
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
