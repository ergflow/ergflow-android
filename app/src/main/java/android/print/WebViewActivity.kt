package android.print

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.ergflow.activity.CACHED_REPORT
import org.ergflow.activity.REPORT_TIMESTAMP
import org.ergflow.activity.WEBVIEW_URL
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
    private var cachedHtmlReportPath: String? = null
    private var cachedPdfReportPath: String? = null

    val pdfPrintAttrs: PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.NA_GOVT_LETTER.asLandscape())
        .setResolution(PrintAttributes.Resolution("RESOLUTION_ID", "RESOLUTION_ID", 600, 600))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    private val outputFileDescriptor: ParcelFileDescriptor?
        get() {
            try {
                val pdfFile = File(cachedPdfReportPath!!)
                pdfFile.createNewFile()
                return ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
                )
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open ParcelFileDescriptor", e)
            }
            return null
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EfActivityWebviewBinding.inflate(layoutInflater)
        val webView = binding.root
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val documentAdapter = webView.createPrintDocumentAdapter("ErgFlow Report")
                documentAdapter.onLayout(
                    null,
                    pdfPrintAttrs,
                    null,
                    object : PrintDocumentAdapter.LayoutResultCallback() {},
                    null
                )
                documentAdapter.onWrite(
                    arrayOf(PageRange.ALL_PAGES),
                    outputFileDescriptor,
                    null,
                    object : PrintDocumentAdapter.WriteResultCallback() {
                        override fun onWriteFinished(pages: Array<PageRange>) {
                            Log.i(TAG, "Finished writing ${pages.size} pages")
                        }
                    }
                )
            }
        }
        setContentView(webView)
        val settings = webView.settings
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webView.setInitialScale(1)
        val path = intent.getStringExtra(WEBVIEW_URL)
        reportTimestamp = intent.getStringExtra(REPORT_TIMESTAMP)
        cachedHtmlReportPath = intent.getStringExtra(CACHED_REPORT)
        cachedPdfReportPath = cachedHtmlReportPath?.replace("html$", "pdf")
        webView.loadUrl("file://$path")
    }

    override fun onBackPressed() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Save report?")

        builder.setPositiveButton("Yes") { _, _ ->
            Log.i(TAG, "Save report $reportTimestamp")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/pdf"
            intent.putExtra(Intent.EXTRA_TITLE, "ErgFlowReport_$reportTimestamp.pdf")
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
        cachedHtmlReportPath?.apply {
            File(this).delete()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WRITE_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> data?.data?.let { uri ->
                    cachedHtmlReportPath?.apply {
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

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "WebViewActivity"
    }
}
