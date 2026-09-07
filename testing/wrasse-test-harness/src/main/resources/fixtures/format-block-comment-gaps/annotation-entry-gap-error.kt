package sample

annotation class Marker(val value: Int)

@Marker(/* tag */1)
class First

@Marker(1/* tag */)
class Second

// expect-error 1:1 format "File is not wrasse-formatted"
