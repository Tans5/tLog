
A log library for Android. 

## Installation

Add the dependency to your build.gradle:
```Groovy
dependencies {
	 // ...
    implementation 'io.github.tans5:tlog:1.0.0'
    // ...
}
```

## Usage

### Initialization
```Kotlin
val log = tLog.Companion.Builder(
    // Required: Directory to store log files
    File(application.externalCacheDir, "AppLog")
)
    // Configure basic parameters (with default values):
    .setMaxSize(1024L * 1024L * 30L) // Maximum total log size (default = 30MB)
    .setLogFilterLevel(LogLevel.Debug) // // Minimum severity level to record
    .setBackgroundThread(HandlerThread("LogThread")) // Dedicated I/O thread
    .setLogToBytesConverter(CustomEncryptor()) // Add encryption layer
    .setInitCallback(object : InitCallback {
        override fun onSuccess() {
            /* Ready for logging */
        }
        override fun onFail(e: Throwable) {
            /* Handle failure */
        }
    }) // Track initialization status
    .build()
```

### Basic Logging
```Kotlin
log.d(TAG, "Debug")
log.i(TAG, "Info")
log.w(TAG, "Waring")
log.e(TAG, "Error", Throwable("TestError"))
```

### Maintenance Operations
```Kotlin
// Ensure all buffered logs are persisted
log.flush() 

// Create compressed archive (ideal for uploads)
log.zipLogFile(outputFile = File("logs.zip"))

// Remove all log files
log.deleteAllLogs()
```