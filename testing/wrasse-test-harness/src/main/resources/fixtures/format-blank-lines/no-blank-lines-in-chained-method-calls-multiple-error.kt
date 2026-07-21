package sample

fun demo(inputTextThatIsLongEnoughToWrap: String): String {
    return inputTextThatIsLongEnoughToWrap


        .uppercase()
        .trim()
}

// expect-error 1:1 format "File is not wrasse-formatted"
