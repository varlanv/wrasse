package sample

class HttpClient {
    fun execute(request: String, userAgent: String): String = request
}

class Service(private val client: HttpClient) {
    fun send(apacheRequest: String, connectionTimeoutMillis: Long): String {
        val future = client.execute(
            apacheRequest + " with timeout " + connectionTimeoutMillis,
            "kryptoid-trader/1.0 (custom-configuration)",
        )
        return future
    }
}