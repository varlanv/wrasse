package sample

annotation class Serializable

/**
   * Doc
 */ @Serializable data class Pair(val a: Int)

// expect-error 1:1 format "File is not wrasse-formatted"
