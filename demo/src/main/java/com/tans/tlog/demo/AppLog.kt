package com.tans.tlog.demo

import android.app.Application
import com.tans.tlog.InitCallback
import com.tans.tlog.tLog
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object AppLog {

    private val log: AtomicReference<tLog?> = AtomicReference(null)

    fun init(application: Application) {
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

    const val TAG = "AppLog"
}