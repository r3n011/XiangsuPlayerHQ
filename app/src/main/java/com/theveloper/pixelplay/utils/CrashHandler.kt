package com.theveloper.pixelplay.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a saved crash log entry.
 */
data class CrashLogData(
    val timestamp: Long,
    val formattedDate: String,
    val exceptionMessage: String,
    val stackTrace: String
) {
    /**
     * Returns the full crash log formatted for display or sharing.
     */
    fun getFullLog(): String {
        return buildString {
            appendLine("=== PixelPlayer Crash Report ===")
            appendLine("Date: $formattedDate")
            appendLine("Exception: $exceptionMessage")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }
    }
}

/**
 * Custom UncaughtExceptionHandler that saves crash information to both
 * SharedPreferences and a file in the app's external files directory,
 * so it can be displayed to the user when the app restarts or
 * retrieved manually from the file system.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val PREFS_NAME = "crash_handler_prefs"
    private const val KEY_HAS_CRASH = "has_crash"
    private const val KEY_TIMESTAMP = "crash_timestamp"
    private const val KEY_EXCEPTION_MESSAGE = "crash_exception_message"
    private const val KEY_STACK_TRACE = "crash_stack_trace"
    private const val CRASH_FILE_NAME = "pixelplay_crash.log"

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Installs this crash handler as the default uncaught exception handler.
     * Should be called at the very start of Application.onCreate().
     */
    fun install(context: Context) {
        try {
            appContext = context.applicationContext
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(this)
            android.util.Log.i("PixelPlay", "CrashHandler installed successfully")
        } catch (e: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to install CrashHandler: ${e.message}")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashLog(throwable)
            saveCrashToFile(throwable)
        } catch (e: Throwable) {
            // Never let the crash handler crash
        }

        // Call the default handler to allow normal crash behavior
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val stackTrace = getStackTraceString(throwable)
        val exceptionMessage = throwable.message ?: throwable.javaClass.simpleName

        prefs.edit().apply {
            putBoolean(KEY_HAS_CRASH, true)
            putLong(KEY_TIMESTAMP, timestamp)
            putString(KEY_EXCEPTION_MESSAGE, exceptionMessage)
            putString(KEY_STACK_TRACE, stackTrace)
            commit() // Synchronous write
        }
    }

    /**
     * Saves the crash to a plain text file in app-specific storage.
     * Path: /sdcard/Android/data/com.r3n011.pixelplay/files/crash/pixelplay_crash.log
     * This is readable by the user without root.
     */
    private fun saveCrashToFile(throwable: Throwable) {
        try {
            val crashDir = File(appContext.getExternalFilesDir(null), "crash")
            if (!crashDir.exists()) crashDir.mkdirs()

            val crashFile = File(crashDir, CRASH_FILE_NAME)
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(timestamp))

            val stackTrace = getStackTraceString(throwable)
            val exceptionMessage = throwable.message ?: throwable.javaClass.simpleName

            val log = buildString {
                appendLine("===== PIXELPLAYER CRASH REPORT =====")
                appendLine("Timestamp: $formattedDate")
                appendLine("Build Type: ${android.os.Build.TYPE}")
                appendLine("Device: ${android.os.Build.MODEL}")
                appendLine("Android Version: ${android.os.Build.VERSION.RELEASE}")
                appendLine("Thread: ${Thread.currentThread().name}")
                appendLine()
                appendLine("Exception: $exceptionMessage")
                appendLine("Exception Type: ${throwable.javaClass.name}")
                appendLine()
                appendLine("===== STACK TRACE =====")
                appendLine(stackTrace)
                appendLine()
                appendLine("===== END =====")
            }

            FileOutputStream(crashFile).use { fos ->
                fos.write(log.toByteArray())
            }
            android.util.Log.e("PixelPlay", "Crash saved to: ${crashFile.absolutePath}")
        } catch (e: Throwable) {
            android.util.Log.e("PixelPlay", "Failed to save crash to file: ${e.message}")
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    /**
     * Returns the path of the crash log file. Returns null if no file exists.
     */
    fun getCrashFilePath(): String? {
        if (!::appContext.isInitialized) return null
        val crashFile = File(File(appContext.getExternalFilesDir(null), "crash"), CRASH_FILE_NAME)
        return if (crashFile.exists()) crashFile.absolutePath else null
    }

    /**
     * Checks if there is a saved crash log from a previous session.
     */
    fun hasCrashLog(): Boolean {
        if (!::appContext.isInitialized) return false
        return prefs.getBoolean(KEY_HAS_CRASH, false)
    }

    /**
     * Retrieves the saved crash log data from SharedPreferences.
     * Returns null if no crash log exists.
     */
    fun getCrashLog(): CrashLogData? {
        if (!hasCrashLog()) return null

        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
        val exceptionMessage = prefs.getString(KEY_EXCEPTION_MESSAGE, "Unknown error") ?: "Unknown error"
        val stackTrace = prefs.getString(KEY_STACK_TRACE, "") ?: ""

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(timestamp))

        return CrashLogData(
            timestamp = timestamp,
            formattedDate = formattedDate,
            exceptionMessage = exceptionMessage,
            stackTrace = stackTrace
        )
    }

    /**
     * Clears the saved crash log.
     * Should be called after the user has acknowledged the crash report.
     */
    fun clearCrashLog() {
        if (!::appContext.isInitialized) return
        prefs.edit().clear().apply()
        try {
            val crashFile = File(File(appContext.getExternalFilesDir(null), "crash"), CRASH_FILE_NAME)
            if (crashFile.exists()) crashFile.delete()
        } catch (_: Throwable) {
            // ignore
        }
    }
}