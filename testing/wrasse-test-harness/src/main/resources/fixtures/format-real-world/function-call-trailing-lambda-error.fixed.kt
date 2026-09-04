package sample

fun runService(
    appName: String,
    workers: Int,
    block: (String) -> Unit,
) {
    block(appName)
}

fun main() {
    runService(
        appName = "exchanges-rest-proxy",
        workers = Runtime.getRuntime().availableProcessors(),
    ) { params -> println(params) }
}