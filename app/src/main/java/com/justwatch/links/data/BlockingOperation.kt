package com.justwatch.links.data

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> blockingOperation(cancel: () -> Unit, block: () -> T): T = coroutineScope {
    suspendCancellableCoroutine { continuation ->
        val task = launch(Dispatchers.IO) {
            try {
                continuation.resume(runInterruptible { block() })
            } catch (error: CancellationException) {
                continuation.cancel(error)
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation {
            cancel()
            task.cancel()
        }
    }
}
