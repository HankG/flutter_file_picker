package com.mr.flutter.plugin.filepicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mr.flutter.plugin.filepicker.FileUtils.maybeRenameGenericMimeDuplicate
import com.mr.flutter.plugin.filepicker.FileUtils.processFiles
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FilePickerDelegate(
    val activity: Activity,
    var pendingResult: MethodChannel.Result? = null
) : ActivityResultListener {

    companion object {
        const val TAG = "FilePickerDelegate"
        const val REQUEST_CODE = 0x4F50
        const val SAVE_FILE_CODE = 0x4F51

        fun finishWithAlreadyActiveError(result: MethodChannel.Result) {
            result.error("already_active", "File picker is already active", null)
        }
    }

    var isMultipleSelection = false
    var type: String? = null
    var compressionQuality = 0
    var allowedExtensions: ArrayList<String>? = null
    var eventSink: EventSink? = null
    var bytes: ByteArray? = null
    var saveFileName: String? = null
    var saveMimeType: String? = null
    var androidSafOptions: java.util.HashMap<*, *>? = null

    // True while processing a pick result whose request was lost, because
    // Android destroyed the activity while the picker was open. Its outcome is
    // stored in LostDataStore for retrieveLostData instead of being dropped.
    private var restoringLostResult = false

    // A retrieveLostData call that arrived while a restored result was still
    // being processed. It is answered once that processing finishes.
    private var lostDataResult: MethodChannel.Result? = null

    /**
     * Answers [result] with the stored outcome of a pick whose activity was
     * destroyed, or `null`. Waits if that outcome is still being processed.
     */
    fun retrieveLostData(result: MethodChannel.Result) {
        if (restoringLostResult) {
            lostDataResult = result
            return
        }
        result.success(LostDataStore.takeResult(activity.applicationContext))
    }

    fun setEventHandler(eventSink: EventSink?) {
        this.eventSink = eventSink
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            SAVE_FILE_CODE -> handleSaveFileResult(resultCode, data)
            REQUEST_CODE -> {
                if (pendingResult == null && !restoreLostRequest()) {
                    false
                } else {
                    handleFilePickerResult(resultCode, data)
                }
            }
            else -> false.also {
                finishWithError(
                    "unknown_activity",
                    "Unknown activity error, please file an issue."
                )
            }
        }
    }

    private fun handleSaveFileResult(resultCode: Int, data: Intent?): Boolean {
        return when (resultCode) {
            Activity.RESULT_OK -> saveFile(data?.data)
            Activity.RESULT_CANCELED -> {
                finishWithSuccess(null)
                false
            }

            else -> false
        }
    }

    private fun saveFile(uri: Uri?): Boolean {
        uri ?: return false
        dispatchEventStatus(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val savedUri = FileUtils.writeBytesData(context = activity, uri, bytes) ?: uri
                val renamedUri = maybeRenameGenericMimeDuplicate(
                    context = activity,
                    uri = savedUri,
                    originalFileName = saveFileName,
                    mimeType = saveMimeType
                )
                Handler(Looper.getMainLooper()).post {
                    finishWithSuccess(renamedUri.path)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error while saving file", e)
                Handler(Looper.getMainLooper()).post {
                    finishWithError("Error while saving file", e.message)
                }
            }
        }
        return true
    }

    private fun handleFilePickerResult(resultCode: Int, data: Intent?): Boolean {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                dispatchEventStatus(true)
                processFiles(activity, data, compressionQuality, type.orEmpty(), androidSafOptions)
                true
            }

            Activity.RESULT_CANCELED -> {
                finishWithSuccess(null)
                true
            }

            else -> false
        }
    }

    /**
     * Restores the options of a request whose activity was destroyed while the
     * picker was open. Returns `false` when there is nothing to restore.
     */
    private fun restoreLostRequest(): Boolean {
        val request = LostDataStore.loadRequest(activity.applicationContext) ?: return false
        type = request.type
        compressionQuality = request.compressionQuality
        androidSafOptions = request.androidSafOptions
        restoringLostResult = true
        return true
    }

    fun setPendingMethodCallResult(result: MethodChannel.Result): Boolean {
        return if (pendingResult == null) {
            pendingResult = result
            true
        } else {
            false
        }
    }

    fun finishWithSuccess(data: Any?) {
        dispatchEventStatus(false)
        if (finishLostResult { context ->
                val files = (data as? ArrayList<*>)?.filterIsInstance<FileInfo>()
                if (files != null) {
                    LostDataStore.saveFiles(context, files)
                } else {
                    LostDataStore.clearRequest(context)
                }
            }) return
        LostDataStore.clearRequest(activity.applicationContext)
        pendingResult?.let {
            it.success(data?.takeIf { it is String }
                ?: (data as? ArrayList<*>)?.mapNotNull { (it as? FileInfo)?.toMap() })
            clearPendingResult()
        }
    }

    fun finishWithError(errorCode: String, errorMessage: String?) {
        dispatchEventStatus(false)
        if (finishLostResult { context ->
                LostDataStore.saveError(context, errorCode, errorMessage)
            }) return
        LostDataStore.clearRequest(activity.applicationContext)
        pendingResult?.error(errorCode, errorMessage, null)
        clearPendingResult()
    }

    private fun dispatchEventStatus(status: Boolean) {
        if (eventSink != null && type != "dir") {
            Handler(Looper.getMainLooper()).post { eventSink?.success(status) }
        }
    }

    /**
     * Stores the outcome of a restored request through [store] and returns
     * `true`, or returns `false` when this is a regular request.
     */
    private fun finishLostResult(store: (android.content.Context) -> Unit): Boolean {
        if (pendingResult != null || !restoringLostResult) return false
        restoringLostResult = false
        val context = activity.applicationContext
        store(context)
        lostDataResult?.let {
            lostDataResult = null
            it.success(LostDataStore.takeResult(context))
        }
        return true
    }

    private fun clearPendingResult() {
        pendingResult = null
    }
}