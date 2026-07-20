package sample

fun demo(): Int {
    if (outerCondition1)
        if (innerCondition1)
            if (innerCondition11) {
                return 0
            } else if (innerCondition12) {
                return 12
            } else if (innerCondition13) {
                return 13
            } else {
                return 14
            }
        else if (innerCondition44) {
            return 1
        } else {
            return 16
        }
    else if (outerCondition2)
        if (innerCondition2) {
            return 2
        } else if (innerCondition3) {
            return 3
        } else {
            return 4
        }
    else
        if (innerCondition4) {
        return 5
    } else {
        return -1
    }
}

val outerCondition1 = true
val outerCondition2 = false
val innerCondition1 = true
val innerCondition11 = true
val innerCondition12 = true
val innerCondition13 = true
val innerCondition2 = true
val innerCondition3 = true
val innerCondition4 = true
val innerCondition44 = true