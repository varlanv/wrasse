package sample

class HttpClient {
    fun execute(request: String, callback: (String) -> Unit): String {
        callback(request)
        return request
    }
}

fun doRequest(client: HttpClient, request: String) {
    val future =
        client.execute(request) { println(it) }
    println(future)
}

// expect-clean
