package sample

fun log(throwable: Throwable?, message: () -> String): String = message() + throwable

fun demo(e: Throwable): String = log(e, { "failed" }) + log(throwable = e, message = { "again" })

// expect-clean
