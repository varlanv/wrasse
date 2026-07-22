package sample

private class Unused

fun describe(): String = "Unused"

// expect-error 3:1 unused-private-class "Private class 'Unused' is unused"
