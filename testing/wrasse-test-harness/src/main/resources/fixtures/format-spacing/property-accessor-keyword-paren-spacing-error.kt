package sample

class Box {
    var value: String = ""
        get () = field
        set (v) {
            field = v
        }
}

// expect-error 1:1 format "File is not wrasse-formatted"
