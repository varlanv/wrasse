package sample

annotation class Marker

/**
   * Doc
 */ @Marker fun pair(a: Int): Int = a

// expect-error 1:1 format "File is not wrasse-formatted"
