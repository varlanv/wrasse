package sample

fun mean(values: List<Double>): Double = values.sum() / values.size

fun pct(value: Double): String = (value * 100).toString()

fun render(xs: List<Double>): String = "mean=${pct(value = mean(values = xs))} n=${xs.size}"

// expect-clean
