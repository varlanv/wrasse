package sample

fun main() {
    val a   =   1
    // wrasse fmt:off
  val   b  =  2   


		val c=3  ;   
   println( a,b )   

    // wrasse fmt:on
    val d   =   4
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 9:12 no-semicolons "Unnecessary semicolon"
