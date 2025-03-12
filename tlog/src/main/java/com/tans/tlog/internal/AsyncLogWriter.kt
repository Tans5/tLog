package com.tans.tlog.internal

import android.os.Handler
import android.os.Message
import com.tans.tlog.LogLevel
import com.tans.tlrucache.disk.DiskLruCache
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue

internal class AsyncLogWriter(
    private val baseDir: File,
    maxSize: Long?,
    private val logFilterLevel: LogLevel,
    private val backgroundExecutor: Executor
) {

    private val maxSize: Long = maxSize ?: DEFAULT_MAX_SIZE

    @Volatile
    private var  writerState: WriterState = WriterState.Initializing

    @Volatile
    private var diskLruCache: DiskLruCache? = null

    private val waitingWriteLogs: LinkedBlockingQueue<String> by lazy {
        LinkedBlockingQueue()
    }

    private val writeLock = this

    private val handler: Handler by lazy {
        object : Handler(backgroundExecutor.getBgThreadLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    WRITE_LOG_MSG -> {
                        writeWaitingLogs()
                        handler.removeMessages(COMMIT_LOG_MSG)
                        handler.sendEmptyMessageDelayed(COMMIT_LOG_MSG, TIMEOUT_COMMIT_LOG_INTERVAL)
                    }

                    COMMIT_LOG_MSG -> {
                        commitLogs()
                    }
                }
            }
        }
    }

    init {
        backgroundExecutor.executeOnBgThread {
            this.diskLruCache = try {
                val c = DiskLruCache.open(
                    directory = baseDir,
                    appVersion = 1,
                    valueCount = 1,
                    maxSize = this.maxSize,
                    deleteDirtyFile = false
                )
                this.writerState = WriterState.InitSuccess
                LibLog.d(TAG, "Init success.")
                if (waitingWriteLogs.isNotEmpty()) {
                    handler.sendEmptyMessage(WRITE_LOG_MSG)
                }
                c
            } catch (e: Throwable) {
                this.writerState = WriterState.InitFail
                LibLog.e(TAG, "Init fail: ${e.message}", e)
                null
            }
        }
    }

    fun writeLog(logLevel: LogLevel, tag: String, msg: String, throwable: Throwable? = null) {
        if (writerState == WriterState.InitFail) {
            LibLog.w(TAG, "Drop log, because of init fail.")
        } else {
            if (logLevel.ordinal >= logFilterLevel.ordinal) {
                if (waitingWriteLogs.size > MAX_WAITING_QUEUE_SIZE) {
                    LibLog.w(TAG, "Drop log, because of waiting queue is full, queueSize=${waitingWriteLogs.size}, maxQueueSize=${MAX_WAITING_QUEUE_SIZE}")
                } else {
                    val time = System.currentTimeMillis()
                    backgroundExecutor.executeOnBgThread {
                        val ls = convertLogToString(
                            time = time,
                            logLevel = logLevel,
                            tag = tag,
                            msg = msg,
                            throwable = throwable
                        )
                        waitingWriteLogs.add(ls)
                        if (writerState == WriterState.InitSuccess) {
                            handler.sendEmptyMessage(WRITE_LOG_MSG)
                        }
                    }
                }
            }
        }
    }



    fun flush() {
        writeWaitingLogs()
        commitLogs()
    }

    private var editor: DiskLruCache.Editor? = null

    private var writer: OutputStreamWriter? = null

    private fun writeWaitingLogs() {
        synchronized(writeLock) {
            val diskLruCache = this@AsyncLogWriter.diskLruCache
            if (waitingWriteLogs.isNotEmpty() && diskLruCache != null) {
                var editor = this.editor
                var writer = this.writer
                try {
                    if (editor == null || writer == null) {
                        writer?.close()
                        editor?.commit()
                        this.writer = null
                        this.editor = null

                        val key = getCacheKey()
                        editor = diskLruCache.edit(key)
                        if (editor == null) {
                            LibLog.e(TAG, "Can't get editor for key: $key")
                        } else {
                            this.editor = editor
                            val f = editor.getFile(0)
                            if (!f.exists()) {
                                f.createNewFile()
                            }
                            writer = FileOutputStream(f, true).writer(Charsets.UTF_8)
                            this.writer = writer
                        }
                    }
                    while (waitingWriteLogs.isNotEmpty()) {
                        val l = waitingWriteLogs.poll()
                        if (l == null) {
                            break
                        } else {
                            writer?.write(l)
                        }
                    }
                } catch (e: Throwable) {
                    try {
                        writer?.close()
                    } catch (_: Throwable) {}
                    try {
                        editor?.abort()
                    } catch (_: Throwable) {}
                    this.writer = null
                    this.editor = null
                    LibLog.e(TAG, "Write log fail: ${e.message}", e)
                }
            }
        }
    }

    private fun commitLogs() {
        synchronized(writeLock) {
            try {
                this.writer?.close()
                this.editor?.commit()
            } catch (e: Throwable) {
                try {
                    this.editor?.abort()
                } catch (_: Throwable) { }
                LibLog.e(TAG, "Commit log fail: ${e.message}", e)
            } finally {
                this.writer = null
                this.editor = null
            }
        }
    }

    companion object {
        // 30 MB
        private const val DEFAULT_MAX_SIZE = 1024L * 1024L * 30L

        private const val WRITE_LOG_MSG = 0

        private const val COMMIT_LOG_MSG = 1

        // 2s
        private const val TIMEOUT_COMMIT_LOG_INTERVAL = 2000L

        private const val MAX_WAITING_QUEUE_SIZE = 2000

        private const val TAG = "AsyncLogWriter"

        private enum class WriterState {
            Initializing,
            InitSuccess,
            InitFail
        }
    }
}