package com.tans.tlog.internal

import android.os.Handler
import android.os.Message
import com.tans.tlog.InitCallback
import com.tans.tlog.LogLevel
import com.tans.tlog.LogToBytesConverter
import com.tans.tlrucache.disk.DiskLruCache
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel.MapMode
import java.util.concurrent.LinkedBlockingQueue

internal class AsyncLogWriter(
    private val baseDir: File,
    maxSize: Long?,
    private val logFilterLevel: LogLevel,
    private val backgroundExecutor: Executor,
    private val initCallback: InitCallback?,
    logToBytesConverter: LogToBytesConverter?
) {

    private val maxSize: Long = maxSize ?: DEFAULT_MAX_SIZE

    private val logToBytesConverter: LogToBytesConverter = logToBytesConverter ?: DefaultLogToBytesConverter

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
                        val fragmentSize = writeWaitingLogs()
                        if (fragmentSize > FRAGMENT_TO_COMMIT_SIZE) {
                            commitLogs()
                        } else {
                            handler.removeMessages(COMMIT_LOG_MSG)
                            handler.sendEmptyMessageDelayed(COMMIT_LOG_MSG, TIMEOUT_COMMIT_LOG_INTERVAL)
                        }
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
                    isAppendMode = true
                )
                this.writerState = WriterState.InitSuccess
                LibLog.d(TAG, "Init success.")
                if (waitingWriteLogs.isNotEmpty()) {
                    handler.sendEmptyMessageDelayed(WRITE_LOG_MSG, 10L)
                }
                initCallback?.onSuccess()
                c
            } catch (e: Throwable) {
                initCallback?.onFail(e)
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
                    val threadName = Thread.currentThread().name
                    backgroundExecutor.executeOnBgThread {
                        val ls = convertLogToString(
                            time = time,
                            threadName = threadName,
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
        synchronized(writeLock) {
            writeWaitingLogs()
            commitLogs()
        }
    }

    fun zipLogFile(outputFile: File, deleteLogs: Boolean): Boolean {
        return synchronized(writeLock) {
            if (writerState != WriterState.InitSuccess) {
                return@synchronized false
            }
            flush()
            val isSuccess = zipFile(
                baseDir = baseDir,
                outputFile = outputFile,
                filter = { f -> f.isFile && f.name != "journal" }
            )
            if (isSuccess && deleteLogs) {
                deleteAllLogs()
            }
            isSuccess
        }
    }

    fun zipLogFile(deleteLogs: Boolean): ByteArray? {
        return synchronized(writeLock) {
            if (writerState != WriterState.InitSuccess) {
                return@synchronized null
            }
            flush()
            val result = zipFile(
                baseDir = baseDir,
                filter = { f -> f.isFile && f.name != "journal" }
            )
            if (result != null && deleteLogs) {
                deleteAllLogs()
            }
            result
        }
    }

    fun deleteAllLogs() {
        synchronized(writeLock) {
            flush()
            val children = baseDir.listFiles() ?: emptyArray()
            for (c in children) {
                if (c.isFile && c.name != "journal") {
                    c.delete()
                }
            }
        }
    }

    private var editor: DiskLruCache.Editor? = null

    private var lastWriteBuffer: MappedByteBuffer? = null

    private var writingFile: RandomAccessFile? = null

    private fun writeWaitingLogs(): Long {
        return synchronized(writeLock) {
            val diskLruCache = this@AsyncLogWriter.diskLruCache
            if (waitingWriteLogs.isNotEmpty() && diskLruCache != null) {
                var editor = this.editor
                var writingFile = this.writingFile
                try {
                    if (editor == null || writingFile == null) {
                        writingFile?.close()
                        editor?.commit()

                        val key = getCacheKey()
                        editor = diskLruCache.edit(key) ?: error("Can't get editor for key: $key")
                        this.editor = editor
                        val f = editor.getFile(0)
                        if (!f.exists()) {
                            f.createNewFile()
                        }
                        writingFile = RandomAccessFile(f, "rw")
                        this.writingFile = writingFile
                    }
                    val bytes = ArrayList<ByteArray>()
                    var bytesSize = 0
                    while (waitingWriteLogs.isNotEmpty()) {
                        val l = waitingWriteLogs.poll()
                        if (l == null) {
                            break
                        } else {
                            bytes.add(logToBytesConverter.convert(l).apply { bytesSize += size })
                        }
                    }
                    if (bytesSize > 0) {
                        val oldLength = writingFile.length()
                        val newLength = bytesSize + oldLength
                        writingFile.setLength(newLength)
                        this.lastWriteBuffer?.force()
                        this.lastWriteBuffer = null
                        val writeBuffer = writingFile.channel.map(MapMode.READ_WRITE, oldLength, bytesSize.toLong())
                        for (b in bytes) {
                            writeBuffer.put(b)
                        }
                        newLength
                    } else {
                        writingFile.length()
                    }
                } catch (e: Throwable) {
                    try {
                        writingFile?.close()
                    } catch (_: Throwable) {}
                    try {
                        editor?.abort()
                    } catch (_: Throwable) {}
                    this.lastWriteBuffer = null
                    this.writingFile = null
                    this.editor = null
                    LibLog.e(TAG, "Write log fail: ${e.message}", e)
                    0L
                }
            } else {
                writingFile?.length() ?: 0L
            }
        }
    }

    private fun commitLogs() {
        synchronized(writeLock) {
            try {
                this.lastWriteBuffer?.force()
                this.writingFile?.close()
                this.editor?.commit()
            } catch (e: Throwable) {
                try {
                    this.writingFile?.close()
                } catch (_: Throwable) {}
                try {
                    this.editor?.abortUnlessCommitted()
                } catch (_: Throwable) { }
                LibLog.e(TAG, "Commit log fail: ${e.message}", e)
            } finally {
                this.lastWriteBuffer = null
                this.writingFile = null
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

        // 128 KB
        private const val FRAGMENT_TO_COMMIT_SIZE = 1024L * 128L

        private const val MAX_WAITING_QUEUE_SIZE = 500

        private const val TAG = "AsyncLogWriter"

        private enum class WriterState {
            Initializing,
            InitSuccess,
            InitFail
        }

        private object DefaultLogToBytesConverter : LogToBytesConverter {
            override fun convert(log: String): ByteArray {
                return log.toByteArray(Charsets.UTF_8)
            }
        }
    }
}