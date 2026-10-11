package com.mr.flutter.plugin.filepicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the in-flight pick request and its result, so a pick survives
 * Android destroying the activity (or the whole process) while the system
 * picker is open.
 *
 * The request options are stored before the picker opens. When a result
 * arrives with no pending request, the delegate restores those options,
 * processes the files as usual and stores the result here instead of dropping
 * it. Dart later reads it once through `retrieveLostData`.
 */
object LostDataStore {
    private const val PREFS_NAME = "flutter_file_picker_lost_data"
    private const val KEY_REQUEST = "pending_request"
    private const val KEY_RESULT = "lost_result"

    /** The options of a pick request, enough to process its result later. */
    data class PendingRequest(
        val type: String,
        val compressionQuality: Int,
        val androidSafOptions: HashMap<*, *>?
    )

    fun saveRequest(context: Context, request: PendingRequest) {
        val json = JSONObject()
            .put("type", request.type)
            .put("compressionQuality", request.compressionQuality)
            .put("androidSafOptions", request.androidSafOptions?.let { JSONObject(it) })
        prefs(context).edit().putString(KEY_REQUEST, json.toString()).apply()
    }

    fun loadRequest(context: Context): PendingRequest? {
        val raw = prefs(context).getString(KEY_REQUEST, null) ?: return null
        return try {
            val json = JSONObject(raw)
            PendingRequest(
                type = json.getString("type"),
                compressionQuality = json.optInt("compressionQuality", 0),
                androidSafOptions = json.optJSONObject("androidSafOptions")?.let { toMap(it) }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearRequest(context: Context) {
        prefs(context).edit().remove(KEY_REQUEST).apply()
    }

    /** Stores the files of an orphaned pick, replacing any previous one. */
    fun saveFiles(context: Context, files: List<FileInfo>) {
        val array = JSONArray()
        files.forEach { array.put(JSONObject(it.toMap())) }
        storeResult(context, JSONObject().put("files", array))
    }

    /** Stores the error of an orphaned pick, replacing any previous one. */
    fun saveError(context: Context, code: String, message: String?) {
        storeResult(
            context,
            JSONObject().put("errorCode", code).put("errorMessage", message)
        )
    }

    /**
     * Returns the stored result as a map for the method channel, or `null`
     * when there is none, and clears it so it is only delivered once.
     */
    fun takeResult(context: Context): HashMap<String, Any?>? {
        val prefs = prefs(context)
        val raw = prefs.getString(KEY_RESULT, null) ?: return null
        prefs.edit().remove(KEY_RESULT).apply()
        return try {
            toMap(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    private fun storeResult(context: Context, json: JSONObject) {
        prefs(context).edit()
            .putString(KEY_RESULT, json.toString())
            .remove(KEY_REQUEST)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun toMap(json: JSONObject): HashMap<String, Any?> {
        val map = HashMap<String, Any?>()
        json.keys().forEach { key -> map[key] = fromJson(json.get(key)) }
        return map
    }

    private fun fromJson(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> toMap(value)
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        else -> value
    }
}
