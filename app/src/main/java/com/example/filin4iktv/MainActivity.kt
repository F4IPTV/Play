package com.example.filin4iktv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Удаляем старые apk из кэша
        cacheDir.listFiles()?.forEach {
            if (it.name.endsWith(".apk")) it.delete()
        }

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidInterface")
        webView.loadUrl("https://f4iptv.github.io/Play/index.html")
    }

    class WebAppInterface(private val context: Context) {

        @JavascriptInterface
        fun startInstallation(groupName: String) {
            Thread {
                try {
                    val configUrl = "https://f4iptv.github.io/Play/config.json"
                    val configText = URL(configUrl).readText()
                    val config = JSONObject(configText)

                    val group = config.getJSONObject("groups").getJSONObject(groupName)
                    val tags = group.getJSONArray("tags")

                    // Установка APK по тегам
                    val apps = config.getJSONArray("apps")
                    for (i in 0 until apps.length()) {
                        val app = apps.getJSONObject(i)
                        if (tagsMatch(tags, app.getJSONArray("tags"))) {
                            val url = app.getString("url")
                            installApk(url)
                        }
                    }

                    // Загрузка backup по тегам
                    val backups = config.getJSONArray("backups")
                    for (i in 0 until backups.length()) {
                        val backup = backups.getJSONObject(i)
                        if (tagsMatch(tags, backup.getJSONArray("tags"))) {
                            val fileUrl = backup.getString("url")
                            val folder = backup.getString("target_folder")
                            downloadFile(fileUrl, folder)
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                }
            }.start()
        }

        private fun tagsMatch(groupTags: JSONArray, itemTags: JSONArray): Boolean {
            for (i in 0 until groupTags.length()) {
                val tag = groupTags.getString(i)
                for (j in 0 until itemTags.length()) {
                    if (tag.equals(itemTags.getString(j), ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun installApk(url: String) {
            Thread {
                try {
                    val fileName = "temp_install_${System.currentTimeMillis()}.apk"
                    val apkFile = File(context.cacheDir, fileName)

                    val input = URL(url).openStream()
                    val output = FileOutputStream(apkFile)
                    input.copyTo(output)
                    input.close()
                    output.close()

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        apkFile
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(intent)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (apkFile.exists()) apkFile.delete()
                    }, 2 * 60 * 1000)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        private fun downloadFile(urlStr: String, targetFolder: String) {
            try {
                val fileName = Uri.parse(urlStr).lastPathSegment ?: return
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val input = BufferedInputStream(connection.inputStream)
                val outFile = File(
                    Environment.getExternalStoragePublicDirectory(targetFolder),
                    fileName
                )
                val output = FileOutputStream(outFile)

                input.copyTo(output)
                input.close()
                output.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
