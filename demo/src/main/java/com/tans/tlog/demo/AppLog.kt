package com.tans.tlog.demo

import android.app.Application
import com.tans.tlog.tLog
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object AppLog {

    private val log: AtomicReference<tLog?> = AtomicReference(null)

    fun init(application: Application) {
        val l = tLog.Companion.Builder(File(application.externalCacheDir, "AppLog"))
            .build()
        log.set(l)
    }

    fun d(tag: String, msg: String) {
        log.get()?.d(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        log.get()?.e(tag, msg, t)
    }

}