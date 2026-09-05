package sample

class Subscription(val id: String)

fun subscribe(clientId: String, kind: String, vararg subscriptions: Subscription): Int =
    subscriptions.size + clientId.length + kind.length

fun demo(): Int = subscribe(clientId = "other", kind = "spot", Subscription("a"), Subscription("b"))