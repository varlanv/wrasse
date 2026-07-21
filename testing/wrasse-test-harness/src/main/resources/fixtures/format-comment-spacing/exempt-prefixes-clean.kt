package sample

//region Constants
const val LIMIT = 10
//endregion

//noinspection UnusedReceiverParameter
fun String.identity(): String = this

//language=SQL
val query = "select 1"

// expect-clean
