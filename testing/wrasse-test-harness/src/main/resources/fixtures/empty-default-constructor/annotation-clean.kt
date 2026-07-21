package sample

annotation class Ann

class Foo @Ann constructor()

// expect-clean
