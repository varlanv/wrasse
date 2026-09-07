package sample

class Api {
    fun one(): Int  =  1
}

fun use(api: Api): Int  =  api.one()

val answer  =  42

// expect-error 1:1 format "File is not wrasse-formatted"
