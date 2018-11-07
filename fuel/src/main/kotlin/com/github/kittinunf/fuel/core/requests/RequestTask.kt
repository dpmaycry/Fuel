package com.github.kittinunf.fuel.core.requests

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import java.io.InterruptedIOException
import java.util.concurrent.Callable

internal fun Request.toTask(): Callable<Response> = RequestTask(this)

internal class RequestTask(internal val request: Request) : Callable<Response> {
    private val interruptCallback: ((Request) -> Unit)? by lazy { executor.interruptCallback }
    private val executor by lazy { request.executionOptions }
    private val client by lazy { executor.client }

    override fun call(): Response = runCatching {
        val modifiedRequest = executor.requestTransformer(request)
        executor.responseTransformer(modifiedRequest, client.executeRequest(modifiedRequest))
    }.fold(
        onSuccess = { response: Response -> response },
        onFailure = { error: Throwable ->
        when (error) {
            is FuelError -> {
                (error.exception as? InterruptedIOException).also {
                    interruptCallback?.invoke(request)
                }
                throw error
            }
            is Exception -> throw FuelError(error)
            else -> throw error
        }
    })
}