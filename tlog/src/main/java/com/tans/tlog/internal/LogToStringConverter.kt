@file:Suppress("NOTHING_TO_INLINE")

package com.tans.tlog.internal

import android.os.Process
import com.tans.tlog.LogLevel
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max


private val sdfDateTimeMsThreadLocal: ThreadLocal<SimpleDateFormat> by lazy {
    ThreadLocal()
}

private val sdfDateTimeMs: SimpleDateFormat
    get() {
        return sdfDateTimeMsThreadLocal.get().let {
            if (it == null) {
                val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                sdfDateTimeMsThreadLocal.set(f)
                f
            } else {
                it
            }
        }
    }

private inline fun String.fixStringLen(maxLen: Int): String {
    return if (this.length > maxLen) this.take(maxLen) else this
}

private const val DATE_TIME_LEN = 23
private const val PID_TID_LEN = 11
private const val TAG_LEN = 23
private const val THREAD_NAME_LEN = 35
private const val LOG_LEVEL_LEN = 3

private const val LOG_HEADER_LEN = DATE_TIME_LEN + PID_TID_LEN + TAG_LEN + THREAD_NAME_LEN + LOG_LEVEL_LEN + 5
private const val LOG_HEADER_FORMAT = "%-${DATE_TIME_LEN}s %-${PID_TID_LEN}s %-${TAG_LEN}s %-${THREAD_NAME_LEN}s %-${LOG_LEVEL_LEN}s "
private const val LOG_MSG_FORMAT = "%-${LOG_HEADER_LEN}s%s"

internal fun convertLogToString(
    time: Long,
    threadName: String,
    tid: Int,
    logLevel: LogLevel,
    tag: String,
    msg: String,
    throwable: Throwable? = null
): String {
    val dataTimeString = sdfDateTimeMs.format(time)
    val pidAndTid = "${Process.myPid()}-$tid".fixStringLen(PID_TID_LEN)
    val fixedTag = tag.fixStringLen(TAG_LEN)
    val threadNameFixed = threadName.fixStringLen(THREAD_NAME_LEN)
    val logLevelString = when (logLevel) {
        LogLevel.Debug -> " D "
        LogLevel.Info -> " I "
        LogLevel.Waring -> " W "
        LogLevel.Error -> " E "
    }
    val header = String.format(
        Locale.US, LOG_HEADER_FORMAT,
        dataTimeString,
        pidAndTid,
        fixedTag,
        threadNameFixed,
        logLevelString
    )
    val result = StringBuilder(header)
    val msgLines = msg.lines()
    result.appendLine(msgLines.getOrNull(0))
    if (msgLines.size > 1) {
        for (i in 1 until msgLines.size) {
            result.appendLine(String.format(Locale.US, LOG_MSG_FORMAT, "", msgLines[i]))
        }
    }
    if (throwable != null) {
        val errorLines = throwable.convertToStrings()
        for (l in errorLines) {
            result.appendLine(String.format(Locale.US, LOG_MSG_FORMAT, "", l))
        }
    }
    return result.toString()
}

internal fun Throwable.convertToStrings(): List<String> {
    val outputStream = ByteArrayOutputStream(64)
    val printStream = PrintStream(outputStream, true, "UTF-8")
    printStackTrace(printStream)
    val s = String(outputStream.toByteArray(), Charsets.UTF_8)
    val lines = s.lines().let {
        if (it.lastOrNull().isNullOrEmpty()) {
            it.take(max( it.size - 1, 0))
        } else {
            it
        }
    }
    return lines
}