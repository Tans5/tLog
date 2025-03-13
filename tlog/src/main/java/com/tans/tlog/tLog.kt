package com.tans.tlog

import android.os.HandlerThread
import android.util.Log
import com.tans.tlog.internal.AsyncLogWriter
import com.tans.tlog.internal.Executor
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Suppress("ClassName")
class tLog private constructor(private val asyncLogWriter: AsyncLogWriter) {

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        asyncLogWriter.writeLog(logLevel = LogLevel.Debug, tag = tag, msg = msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        asyncLogWriter.writeLog(logLevel = LogLevel.Info, tag = tag, msg = msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        asyncLogWriter.writeLog(logLevel = LogLevel.Waring, tag = tag, msg = msg)
    }

    fun e(tag: String, msg: String, t: Throwable?) {
        Log.e(tag, msg, t)
        asyncLogWriter.writeLog(logLevel = LogLevel.Error, tag = tag, msg = msg, throwable = t)
    }

    /**
     * Need work on background thread.
     *
     */
    fun flush() {
        asyncLogWriter.flush()
    }

    /**
     * Need work on background thread.
     *
     * @param outputFile the output zip file.
     * @param deleteLogs if success, delete logs.
     * @return true is success, false is fail.
     */
    fun zipLogFile(outputFile: File, deleteLogs: Boolean = false): Boolean {
       return asyncLogWriter.zipLogFile(outputFile = outputFile, deleteLogs = deleteLogs)
    }

    /**
     * Need work on background thread.
     *
     * @param deleteLogs if success, delete logs.
     * @return if result is null it means fail.
     */
    fun zipLogFile(deleteLogs: Boolean = false): ByteArray? {
        return asyncLogWriter.zipLogFile(deleteLogs = deleteLogs)
    }

    /**
     * Need work on background thread.
     *
     */
    fun deleteAllLogs() {
        return asyncLogWriter.deleteAllLogs()
    }

    companion object {
        private val usedBaseDir: ConcurrentHashMap<String, Unit> = ConcurrentHashMap()

        class Builder(private val baseDir: File) {

            init {
                if (usedBaseDir.putIfAbsent(baseDir.canonicalPath, Unit) != null) {
                    error("$baseDir was already taken.")
                }
            }

            private var maxSize: Long? = null

            private var logFilterLevel: LogLevel = LogLevel.Debug

            private var backgroundThread: HandlerThread? = null

            private var initCallback: InitCallback? = null

            private var logToBytesConverter: LogToBytesConverter? = null

            fun setMaxSize(maxSize: Long): Builder {
                assert(maxSize > 0L) { "Max size must greater than 0" }
                this.maxSize = maxSize
                return this
            }

            fun setLogFilterLevel(logLevel: LogLevel): Builder {
                this.logFilterLevel = logLevel
                return this
            }

            fun setBackgroundThread(bgThread: HandlerThread): Builder {
                this.backgroundThread = bgThread
                return this
            }

            fun setInitCallback(initCallback: InitCallback): Builder {
                this.initCallback = initCallback
                return this
            }

            fun setLogToBytesConverter(converter: LogToBytesConverter): Builder {
                this.logToBytesConverter = converter
                return this
            }

            fun build(): tLog {
                return tLog(
                    asyncLogWriter = AsyncLogWriter(
                        baseDir = baseDir,
                        maxSize = maxSize,
                        logFilterLevel = logFilterLevel,
                        backgroundExecutor = Executor(backgroundThread),
                        initCallback = initCallback,
                        logToBytesConverter = logToBytesConverter
                    )
                )
            }
        }
    }
}