package sample

fun describe(nameParameter: String, ageParameter: Int): String {
    return "$nameParameter is $ageParameter"
}

// expect-error 1:1 format "File is not wrasse-formatted"
