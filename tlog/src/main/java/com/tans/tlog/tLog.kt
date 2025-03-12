package com.tans.tlog

import android.os.HandlerThread
import android.util.Log
import com.tans.tlog.internal.AsyncLogWriter
import com.tans.tlog.internal.Executor
import java.io.File

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

    companion object {

        class Builder(private val baseDir: File) {

            private var maxSize: Long? = null

            private var logFilterLevel: LogLevel = LogLevel.Debug

            private var backgroundThread: HandlerThread? = null

            fun setMaxSize(maxSize: Long) {
                assert(maxSize > 0L) { "Max size must greater than 0" }
                this.maxSize = maxSize
            }

            fun setLogFilterLevel(logLevel: LogLevel) {
                this.logFilterLevel = logLevel
            }

            fun setBackgroundThread(bgThread: HandlerThread) {
                this.backgroundThread = bgThread
            }


            fun build(): tLog {
                return tLog(
                    asyncLogWriter = AsyncLogWriter(
                        baseDir = baseDir,
                        maxSize = maxSize,
                        logFilterLevel = logFilterLevel,
                        backgroundExecutor = Executor(backgroundThread)
                    )
                )
            }
        }
    }
}