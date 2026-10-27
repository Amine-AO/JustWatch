package com.justwatch.links.data

import android.content.Context
import com.justwatch.links.AppUpdateInfo
import com.justwatch.links.AppUpdates
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

internal class UpdateRepository(private val context: Context) {
    suspend fun latest(): AppUpdateInfo? = operation { operation ->
        AppUpdates.removeObsoleteDownload(context)
        AppUpdates.latest(operation)
    }

    sealed interface Download {
        data class Progress(val percent: Int) : Download

        data class Complete(val file: File) : Download
    }

    fun download(info: AppUpdateInfo): Flow<Download> = channelFlow {
        val file = operation { operation ->
            AppUpdates.download(context, info, operation) { trySend(Download.Progress(it)) }
        }
        send(Download.Complete(file))
    }

    suspend fun verifiedDownload(): File = operation { operation ->
        AppUpdates.downloaded(context).also {
            operation.check()
            AppUpdates.verifyArchive(context, it)
            operation.check()
        }
    }

    private suspend fun <T> operation(block: (AppUpdates.Operation) -> T): T {
        val operation = AppUpdates.Operation()
        return try {
            blockingOperation(operation::cancel) { block(operation) }
        } finally {
            operation.cancel()
        }
    }
}
