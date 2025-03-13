package com.tans.tlog.demo

import android.view.View
import com.tans.tlog.demo.databinding.ActivityMainBinding
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.ContentViewFitSystemWindow
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@SystemBarStyle
@ContentViewFitSystemWindow
class MainActivity : BaseCoroutineStateActivity<Unit>(Unit) {

    override val layoutId: Int = R.layout.activity_main

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ActivityMainBinding.bind(contentView)
        viewBinding.flushLogBt.clicks(this, clickWorkOn = Dispatchers.IO) {
            AppLog.d(TAG, "Request flush logs")
            AppLog.flushLogs()
            AppLog.d(TAG, "Logs was flushed.")
        }

        viewBinding.deleteLogBt.clicks(this, clickWorkOn = Dispatchers.IO) {
            AppLog.d(TAG, "Request delete logs")
            AppLog.deleteAllLogs()
            AppLog.d(TAG, "Logs was deleted.")
        }

        viewBinding.zipLogBt.clicks(this, clickWorkOn = Dispatchers.IO) {
            AppLog.d(TAG, "Request zip logs")
            AppLog.zipLogs()
            AppLog.d(TAG, "Log was zipped.")
        }
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() { }

    companion object {
        const val TAG = "MainActivity"
    }
}