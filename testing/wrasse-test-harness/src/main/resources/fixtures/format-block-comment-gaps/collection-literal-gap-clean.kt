package sample

annotation class Marker(val values: Array<String>)

@Marker([/* head */ "a", "b"])
class First

@Marker(["a" /* tail */, "b"])
class Second

// expect-clean
