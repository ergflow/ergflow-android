package org.ergflow.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.ergflow.activity.databinding.EfActivityWebviewBinding
import java.io.File

const val WRITE_REQUEST_CODE = 101
const val TAG = "WebViewActivity"

/***
 * Activity for displaying the html reports.
 */
class WebViewActivity : Activity() {
    private lateinit var binding: EfActivityWebviewBinding
    private var reportTimestamp: String? = null
    private var cachedReportPath: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EfActivityWebviewBinding.inflate(layoutInflater)
        val webView = binding.root
        setContentView(webView)
        val settings = webView.settings
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        val path = intent.getStringExtra(WEBVIEW_URL)
        reportTimestamp = intent.getStringExtra(REPORT_TIMESTAMP)
        cachedReportPath = intent.getStringExtra(CACHED_REPORT)
        webView.loadUrl("file://$path")
    }

    override fun onBackPressed() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Save report?")

        builder.setPositiveButton("Yes") { _, _ ->
            Log.i(TAG, "Save report $reportTimestamp")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "text/html"
            intent.putExtra(Intent.EXTRA_TITLE, "ErgFlowReport_$reportTimestamp.html")
            startActivityForResult(intent, WRITE_REQUEST_CODE)
        }

        builder.setNegativeButton("No") { _, _ ->
            // do nothing
            Log.i(TAG, "Cancel")
            deleteCachedReport()
            super.onBackPressed()
        }

        builder.show()
    }

    private fun deleteCachedReport() {
        cachedReportPath?.apply {
            File(this).delete()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WRITE_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> data?.data?.let { uri ->
                    cachedReportPath?.apply {
                        val source = File(this)
                        if (source.exists()) {
                            Log.i(TAG, "Saving ${source.absolutePath} to $uri")
                            contentResolver?.openOutputStream(uri)?.use { out ->
                                source.inputStream().use { it.copyTo(out) }
                            }
                            source.deleteOnExit()
                        } else {
                            Log.w(TAG, "Source ${source.absolutePath} does not exist")
                            showToast("Unable to save the report")
                        }
                    }
                }
                RESULT_CANCELED -> {
                }
            }
        }
        super.onBackPressed()
    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(@Suppress("SameParameterValue") text: String) {
        runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }
}