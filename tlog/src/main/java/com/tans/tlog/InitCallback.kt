package com.tans.tlog

interface InitCallback {

    fun onSuccess()

    fun onFail(e: Throwable)

}