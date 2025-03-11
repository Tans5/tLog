package com.tans.tlog.internal

import com.tans.tlog.LogLevel
import java.io.File

internal class AsyncLogWriter(
    private val baseDir: File,
    maxSize: Long?,
    private val backgroundExecutor: Executor
) {

    private val maxSize: Long = maxSize ?: DEFAULT_MAX_SIZE

    init {
        // TODO:
    }

    fun writeLog(logLevel: LogLevel, tag: String, msg: String, throwable: Throwable? = null) {
        // TODO:
    }

    companion object {
        // 30 MB
        private const val DEFAULT_MAX_SIZE = 1024L * 1024L * 30L
    }
}