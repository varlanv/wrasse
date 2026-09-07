package sample

class Api {
    fun one(): Int = 1
}

fun use(api: Api): Int = api.one()

val answer = 42