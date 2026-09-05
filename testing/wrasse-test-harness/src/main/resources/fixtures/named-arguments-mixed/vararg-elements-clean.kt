package sample

class Subscription(val id: String)

fun subscribe(clientId: String, vararg subscriptions: Subscription): Int = subscriptions.size + clientId.length

fun demo(): Int = subscribe(
    clientId = "other",
    Subscription("a"),
    Subscription("b"),
)

// expect-clean
