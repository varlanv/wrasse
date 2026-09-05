package sample

class Verdict(val ruinProbability: Double)

object MonthlyVerdict {
    fun compute(
        timedReturnsByTradeDate: List<Pair<String, Double>>,
        sizingFractionOfCapital: Double,
    ): Verdict? = Verdict(sizingFractionOfCapital)
}

class Connection

object Nats {
    fun newConnection(url: String): Connection = Connection()

    fun newPubSub(
        connection: Connection,
        parentScope: String,
        callTimeout: Long,
    ): String = parentScope
}

fun demo(
    natsUrl: String,
    infraScope: String,
    callTimeout: Long,
) {
    val result =
        MonthlyVerdict.compute(
            timedReturnsByTradeDate =
            (1..8).map { "2026-01-0$it" to -0.60 },
            sizingFractionOfCapital = 1.0,
        )!!
    println(result.ruinProbability)
    val pubSub = Nats.newPubSub(
        connection =
        Nats.newConnection(url = natsUrl),
        parentScope = infraScope,
        callTimeout = callTimeout,
    )
    println(pubSub)
}

// expect-error 1:1 format "File is not wrasse-formatted"
