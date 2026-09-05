package sample

class Subscription(val id: String)

fun subscribe(clientId: String, kind: String, vararg subscriptions: Subscription): Int =
    subscriptions.size + clientId.length + kind.length

fun demo(): Int = subscribe(clientId = "other", "spot", Subscription("a"), Subscription("b"))

// expect-error 8:28 named-arguments "Positional arguments should be named"
