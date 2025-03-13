package com.tans.tlog.demo

import android.app.Application
import com.tans.tlog.InitCallback
import com.tans.tlog.LogLevel
import com.tans.tlog.tLog
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object AppLog {

    private val log: AtomicReference<tLog?> = AtomicReference(null)

    @Volatile
    private var application: Application? = null

    fun init(application: Application) {
        this.application = application
        val l = tLog.Companion.Builder(File(application.externalCacheDir, "AppLog"))
            .setInitCallback(object : InitCallback {
                override fun onSuccess() {
                    d(TAG, "Init log success.")
                }

                override fun onFail(e: Throwable) {
                    e(TAG, "Init fail: ${e.message}", e)
                }
            })
            .build()
        log.set(l)
    }

    fun d(tag: String, msg: String) {
        log.get()?.d(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        log.get()?.e(tag, msg, t)
    }

    fun flushLogs() {
        log.get()?.flush()
    }

    fun deleteAllLogs() {
        log.get()?.deleteAllLogs()
    }

    fun zipLogs() {
        log.get()?.zipLogFile(File(application!!.externalCacheDir, "logs.zip"))
    }

    const val TAG = "AppLog"
}