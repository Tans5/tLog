package com.tans.tlog.internal

import java.text.SimpleDateFormat
import java.util.Locale

private val sdfDateThreadLocal: ThreadLocal<SimpleDateFormat> by lazy {
    ThreadLocal()
}

private val sdfDate: SimpleDateFormat
    get() {
        return sdfDateThreadLocal.get().let {
            if (it == null) {
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdfDateThreadLocal.set(f)
                f
            } else {
                it
            }
        }
    }

fun getCacheKey(): String {
    val now = System.currentTimeMillis()
    return sdfDate.format(now)
}