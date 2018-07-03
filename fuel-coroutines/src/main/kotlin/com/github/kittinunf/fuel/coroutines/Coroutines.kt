import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.core.Request.Companion.byteArrayDeserializer
import com.github.kittinunf.fuel.core.Request.Companion.stringDeserializer
import com.github.kittinunf.result.Result
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import java.nio.charset.Charset

private suspend fun <T : Any, U : Deserializable<T>> Request.await(
        deserializable: U
): Triple<Request, Response, Result<T, FuelError>> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            response(deserializable) { request: Request, response: Response, result: Result<T, FuelError> ->
                result.fold({
                    continuation.resume(Triple(request, response, result))
                }, {
                    continuation.resumeWithException(it.exception)
                })
            }
        }


suspend fun Request.awaitResponse(): Triple<Request, Response, Result<ByteArray, FuelError>> =
        await(byteArrayDeserializer())

suspend fun Request.awaitString(
        charset: Charset = Charsets.UTF_8
): Triple<Request, Response, Result<String, FuelError>> = await(stringDeserializer(charset))

suspend fun <U : Any> Request.awaitObject(
        deserializable: ResponseDeserializable<U>
): Triple<Request, Response, Result<U, FuelError>> = await(deserializable)

suspend fun Request.awaitResponseResult(): ByteArray = awaitResponse().third.get()

suspend fun Request.awaitStringResult(
        charset: Charset = Charsets.UTF_8
): String = awaitString(charset).third.get()

/**
 * This function will throw the an exception if an error is thrown either at the HTTP level
 * or during deserialization
 *
 * @param deserializable
 *
 * @return Result object
 * */
suspend fun <U : Any> Request.awaitObjectResult(
        deserializable: ResponseDeserializable<U>
): U = await(deserializable).third.get()

/**
 * This function catches both server errors and Deserialization Errors
 *
 * @param deserializable
 *
 * @return Result object
 * */
suspend fun <U : Any> Request.awaitSafelyObjectResult(
        deserializable: ResponseDeserializable<U>
): Result<U, FuelError> = try {
    await(deserializable).third
} catch (e: Exception) {
    val fuelError = when (e) {
        is FuelError -> e
        else -> FuelError(e)
    }
    Result.Failure(fuelError)
}
