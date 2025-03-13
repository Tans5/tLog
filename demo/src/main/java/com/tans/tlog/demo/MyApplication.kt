package com.tans.tlog.demo

import android.app.Application
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AutoApplySystemBarAnnotation.init(this)

        // DebugLog test thread.
        Thread({

            while (true) {
                AppLog.d("DebugTest", "DebugTest: ${System.currentTimeMillis()}")
                Thread.sleep(1000L)
            }
        }, "DebugTestThread").start()

        // Error test thread.
        Thread({
            while (true) {
                try {
                    error("TestError")
                } catch (e: Throwable) {
                    AppLog.e("ErrorTest", "ErrorTest: ${System.currentTimeMillis()}", e)
                }
                Thread.sleep(2000L)
            }
        }, "ErrorTestThread").start()
    }
}