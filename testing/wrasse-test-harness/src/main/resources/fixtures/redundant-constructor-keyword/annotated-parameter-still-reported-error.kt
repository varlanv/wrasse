package sample

annotation class Ann

class AnnotatedParam constructor(@Ann x: Double)

// expect-error 5:22 redundant-constructor-keyword "Redundant constructor keyword"
