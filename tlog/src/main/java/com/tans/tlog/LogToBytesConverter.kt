package com.tans.tlog

interface LogToBytesConverter {
    fun convert(log: String) : ByteArray
}