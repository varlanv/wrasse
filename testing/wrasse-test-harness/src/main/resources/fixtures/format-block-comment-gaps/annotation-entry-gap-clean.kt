package sample

annotation class Marker(val value: Int)

@Marker(/* tag */ 1)
class First

@Marker(1 /* tag */)
class Second

// expect-clean
