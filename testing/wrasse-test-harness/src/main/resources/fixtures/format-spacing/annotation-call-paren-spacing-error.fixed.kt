package sample

annotation class Ann(val value: String)

@Ann("marker")
fun noop() = Unit