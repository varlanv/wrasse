package sample

class Box<T>

fun sampleProjection(box: Box<out /** raw */ Any>) {}

class Pair2<A, B>

fun sampleArgumentList(
    p: Pair2<
        /** raw */
        Int,
        String,
    >,
) {}

class Foo<in /** raw */ T>

class Bar<
    /** raw */
    T,
>

// expect-error 5:35 kdoc-placement "A KDoc is not allowed inside a 'type_projection'"
// expect-error 11:9 kdoc-placement "A KDoc is not allowed inside a 'type_argument_list'"
// expect-error 17:14 kdoc-placement "A KDoc is not allowed inside a 'type_parameter'"
// expect-error 20:5 kdoc-placement "A KDoc is not allowed inside a 'type_parameter_list'"
