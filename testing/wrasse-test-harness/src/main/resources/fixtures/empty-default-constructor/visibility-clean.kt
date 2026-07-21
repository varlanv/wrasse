package sample

class Foo private constructor()

class Bar internal constructor()

open class Baz protected constructor()

// expect-clean
