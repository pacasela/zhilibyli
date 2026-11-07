package app.zhilibyli.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import app.zhilibyli.BuildConfig
import app.zhilibyli.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Проверка обновлений APK.
 *
 * Манифест версии лежит на сайте. Если versionCode в нём больше установленного,
 * показываем предложение скачать и установить актуальную сборку. Для обязательных
 * обновлений в манифесте задаётся minVersionCode: тогда диалог нельзя закрыть.
 */
class UpdateManager(private val activity: AppCompatActivity) {

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var dialog: AlertDialog? = null
    private var downloadId: Long = -1L
    private var downloadFileName: String? = null
    private var pendingInstall: File? = null
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id <= 0L || id != downloadId) return
            finishDownload(id)
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(activity, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    fun check() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!isNetworkAvailable()) return
        activity.lifecycleScope.launch {
            val manifest = withContext(Dispatchers.IO) { fetchManifest() } ?: return@launch
            val latestCode = manifest.optInt("versionCode", 0)
            if (latestCode <= BuildConfig.VERSION_CODE) return@launch

            val mandatory = manifest.optInt("minVersionCode", 0) > BuildConfig.VERSION_CODE
            val versionName = manifest.optString("versionName").ifBlank { latestCode.toString() }
            val notes = manifest.optString("notes").trim().take(500)
            val apkUrl = resolveApkUrl(manifest.optString("apkUrl")) ?: return@launch

            val now = System.currentTimeMillis()
            val lastCode = prefs.getInt(KEY_LAST_PROMPT_VERSION, 0)
            val lastAt = prefs.getLong(KEY_LAST_PROMPT_AT, 0L)
            if (!mandatory && lastCode == latestCode && now - lastAt < PROMPT_INTERVAL_MS) return@launch

            prefs.edit()
                .putInt(KEY_LAST_PROMPT_VERSION, latestCode)
                .putLong(KEY_LAST_PROMPT_AT, now)
                .apply()

            withContext(Dispatchers.Main) {
                showUpdateDialog(versionName, notes, mandatory, apkUrl)
            }
        }
    }

    /** Вызывать из onResume: после выдачи разрешения на установку пробуем снова. */
    fun retryPendingInstall() {
        val file = pendingInstall ?: return
        if (!file.isFile) {
            pendingInstall = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        pendingInstall = null
        openInstaller(file)
    }

    fun destroy() {
        dialog?.dismiss()
        dialog = null
        if (receiverRegistered) {
            runCatching { activity.unregisterReceiver(downloadReceiver) }
            receiverRegistered = false
        }
    }

    private fun showUpdateDialog(
        versionName: String,
        notes: String,
        mandatory: Boolean,
        apkUrl: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing == true) return

        val message = buildString {
            append(
                activity.getString(
                    if (mandatory) R.string.update_force_message else R.string.update_message,
                    versionName,
                ),
            )
            if (notes.isNotBlank()) {
                append("\n\n")
                append(notes)
            }
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ -> startDownload(apkUrl, versionName) }

        if (!mandatory) {
            builder.setNegativeButton(R.string.update_later, null)
        }
        builder.setCancelable(!mandatory)

        dialog = builder.create().also { dialog ->
            dialog.setCanceledOnTouchOutside(!mandatory)
            dialog.show()
        }
    }

    private fun startDownload(apkUrl: String, versionName: String) {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            toast(R.string.update_download_failed)
            return
        }

        val safeVersion = versionName.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val fileName = "zhilibyli-$safeVersion.apk"

        try {
            val existing = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (existing.isFile) existing.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(activity.getString(R.string.update_download_title))
                .setDescription(activity.getString(R.string.update_download_description, versionName))
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            downloadId = manager.enqueue(request)
            downloadFileName = fileName
            toast(R.string.update_downloading)
        } catch (_: Exception) {
            toast(R.string.update_download_failed)
        }
    }

    private fun finishDownload(id: Long) {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        var status = DownloadManager.STATUS_FAILED
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            toast(R.string.update_download_failed)
            return
        }

        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = downloadFileName
            ?.let { File(dir, it) }
            ?.takeIf { it.isFile }
            ?: return
        install(file)
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstall = file
            AlertDialog.Builder(activity)
                .setTitle(R.string.update_install_title)
                .setMessage(R.string.update_install_permission)
                .setPositiveButton(R.string.update_open_settings) { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}"),
                            ),
                        )
                    }
                }
                .setNegativeButton(R.string.update_cancel, null)
                .show()
            return
        }
        openInstaller(file)
    }

    private fun openInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            toast(R.string.update_install_failed)
        }
    }

    private fun fetchManifest(): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("${BuildConfig.UPDATE_MANIFEST_URL}?t=${System.currentTimeMillis()}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) return null
            val raw = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_MANIFEST_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
            JSONObject(raw)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun resolveApkUrl(value: String): String? {
        if (value.isBlank()) return null
        return try {
            val manifest = URL(BuildConfig.UPDATE_MANIFEST_URL)
            val apk = URL(manifest, value)
            if (apk.protocol != "https") return null
            val host = apk.host.lowercase(Locale.US)
            if (host != "zhilibyli.online" && host != "www.zhilibyli.online") return null
            apk.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        return cm.activeNetwork != null
    }

    private fun toast(resId: Int) {
        Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREFS = "zhili_updates"
        private const val KEY_LAST_PROMPT_VERSION = "last_prompt_version"
        private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
        private const val PROMPT_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
