package com.tans.tlog.internal

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

internal class Executor(bgThread: HandlerThread?) {

    private val bgThread: HandlerThread = bgThread ?: defaultBgThread

    private val bgThreadHandler: Handler

    init {
        if (!this.bgThread.isAlive) {
            LibLog.d(TAG, "BgThread is not start, do start.")
            this.bgThread.start()
        }
        while (this.bgThread.looper == null) { // Wait bg thread active.
            Thread.sleep(3)
        }
        bgThreadHandler = Handler(this.bgThread.looper)
    }

    fun executeOnBgThread(r: Runnable) {
        bgThreadHandler.post(r)
    }

    fun getBgThreadLooper(): Looper = bgThread.looper

    companion object {
        private const val TAG = "Executor"
        private val defaultBgThread: HandlerThread by lazy {
            object : HandlerThread("tlog-default") {
                override fun onLooperPrepared() {
                    super.onLooperPrepared()
                    LibLog.d(TAG, "default background thread prepared.")
                }
            }
        }
    }
}