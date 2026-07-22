package sample

fun main() {
    try {
        throw Throwable()
    } catch (e: ArrayIndexOutOfBoundsException) {
        throw Error()
    } catch (e: Error) {
        throw Exception()
    } catch (e: Exception) {
    } catch (e: IllegalMonitorStateException) {
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException()
    } catch (e: Throwable) {
    } catch (e: RuntimeException) {
        throw NullPointerException()
    } catch (e: NullPointerException) {
    }
}

// expect-error 6:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 8:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 10:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 11:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 12:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 14:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 15:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
// expect-error 17:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
