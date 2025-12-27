package ir.patraplus.webui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recordsContainer: View
    private lateinit var recordsRecycler: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var recordsTitle: TextView
    private val javaScriptInjector = JavaScriptInjector()
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var autoLoginAttempted = false
    private lateinit var recordStore: RecordStore
    private val records = mutableListOf<CustomerRecord>()
    private lateinit var recordAdapter: RecordAdapter
    private var currentFilter: RecordStatus? = null

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            fileChooserCallback?.onReceiveValue(uris.toTypedArray())
            fileChooserCallback = null
        }

    private val overlayTapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runExtraction()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        webView = findViewById(R.id.webView)

        WebViewHolder.setWebView(webView)
        configureWebView(webView)

        recordsContainer = findViewById(R.id.recordsContainer)
        recordsRecycler = findViewById(R.id.recordsRecycler)
        emptyState = findViewById(R.id.emptyState)
        recordsTitle = findViewById(R.id.recordsTitle)

        recordStore = RecordStore(this)
        records.addAll(recordStore.load())
        recordAdapter = RecordAdapter { record ->
            showRecordDetail(record)
        }
        recordsRecycler.layoutManager = LinearLayoutManager(this)
        recordsRecycler.adapter = recordAdapter

        setupDrawer()
        webView.loadUrl("https://patraplus.ir/user")
        ensureOverlayPermission()

    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_OVERLAY_TAP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayTapReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(overlayTapReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(overlayTapReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        WebViewHolder.clear()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        startService(intent)
    }

    private fun configureWebView(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!autoLoginAttempted && url?.contains("/user") == true) {
                    autoLoginAttempted = true
                    injectAutoLogin()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                filePickerLauncher.launch("*/*")
                return true
            }
        }
    }

    private fun runExtraction() {
        val scripts = javaScriptInjector.availableScripts()
        if (scripts.isEmpty()) {
            Toast.makeText(this, "No scripts registered.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Automation")
            .setItems(scripts.toTypedArray()) { _, which ->
                javaScriptInjector.inject(scripts[which]) { result ->
                    handleExtractionResult(result)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun injectAutoLogin() {
        val script = """
            (function() {
                const usernameField = document.querySelector(
                    "input[name='username'], input[name='user'], input[name*='user'], input[type='text']"
                );
                const passwordField = document.querySelector("input[type='password']");
                const form = passwordField ? passwordField.closest('form') : null;
                if (usernameField && passwordField) {
                    usernameField.value = "L-khoram";
                    passwordField.value = "L@khoram1234";
                    if (form) {
                        form.submit();
                    } else {
                        const submitButton = document.querySelector(
                            "button[type='submit'], input[type='submit']"
                        );
                        if (submitButton) {
                            submitButton.click();
                        }
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun handleExtractionResult(result: String?) {
        val payload = decodeJavaScriptPayload(result)
        if (payload.isNullOrBlank()) {
            showResultDialog("هیچ داده‌ای دریافت نشد.")
            return
        }

        val token = runCatching { JSONTokener(payload).nextValue() }.getOrNull()
        when (token) {
            is JSONArray -> {
                val parsed = parseRecords(token)
                val resultInfo = recordStore.upsertAll(parsed)
                records.clear()
                records.addAll(resultInfo.records)
                if (currentFilter != null) {
                    showRecords(currentFilter!!)
                }
                val message = if (resultInfo.addedCount > 0) {
                    "✅ ${resultInfo.addedCount} مورد جدید ثبت شد."
                } else {
                    "هیچ مورد جدیدی برای ثبت وجود نداشت."
                }
                showResultDialog(message)
            }
            is JSONObject -> {
                val message = token.optString("error", payload)
                showResultDialog(message)
            }
            else -> showResultDialog(payload)
        }
    }

    private fun parseRecords(array: JSONArray): List<CustomerRecord> {
        val list = mutableListOf<CustomerRecord>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                CustomerRecord(
                    name = obj.optString("نام"),
                    mobile = obj.optString("شماره موبایل"),
                    phone = obj.optString("شماره تلفن"),
                    province = obj.optString("استان"),
                    city = obj.optString("شهر"),
                    postalCode = obj.optString("کد ارسال"),
                    address = obj.optString("آدرس"),
                    notes = obj.optString("توضیحات"),
                    registeredAt = obj.optString("تاریخ ثبت"),
                    seller = obj.optString("فروشنده"),
                    deliveryStatus = obj.optString("وضعیت")
                )
            )
        }
        return list
    }

    private fun decodeJavaScriptPayload(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        val token = JSONTokener(result).nextValue()
        return if (token is String) token else token?.toString()
    }

    private fun showResultDialog(content: String) {
        val messageView = layoutInflater.inflate(R.layout.dialog_result, null)
        val textView = messageView.findViewById<TextView>(R.id.resultText)
        textView.text = content

        AlertDialog.Builder(this)
            .setTitle("Extraction Result")
            .setView(messageView)
            .setPositiveButton("مشاهده رکوردها") { _, _ ->
                showRecords()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_web -> showWebView()
                R.id.nav_all -> showRecords()
                R.id.nav_pending -> showRecords(RecordStatus.PENDING)
                R.id.nav_accepted -> showRecords(RecordStatus.ACCEPTED)
                R.id.nav_rejected -> showRecords(RecordStatus.REJECTED)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navigationView.setCheckedItem(R.id.nav_web)
        showWebView()
    }

    private fun showWebView() {
        currentFilter = null
        toolbar.title = "پنل اصلی"
        recordsContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
        navigationView.setCheckedItem(R.id.nav_web)
    }

    private fun showRecords(status: RecordStatus? = null) {
        currentFilter = status
        val title = status?.label ?: "همه مشتریان"
        toolbar.title = title
        recordsTitle.text = title
        val filtered = if (status == null) records else records.filter { it.status == status }
        recordAdapter.submitList(filtered) {
            recordsRecycler.scheduleLayoutAnimation()
        }
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recordsContainer.visibility = View.VISIBLE
        webView.visibility = View.GONE
        navigationView.setCheckedItem(
            when (status) {
                null -> R.id.nav_all
                RecordStatus.PENDING -> R.id.nav_pending
                RecordStatus.ACCEPTED -> R.id.nav_accepted
                RecordStatus.REJECTED -> R.id.nav_rejected
            }
        )
    }

    private fun showRecordDetail(record: CustomerRecord) {
        val detailView = layoutInflater.inflate(R.layout.dialog_record_detail, null)
        detailView.findViewById<TextView>(R.id.detailName).text =
            record.name.ifBlank { "بدون نام" }
        detailView.findViewById<TextView>(R.id.detailMobile).text =
            record.mobile.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailPhone).text =
            record.phone.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailLocation).text =
            "${record.province.ifBlank { "نامشخص" }} - ${record.city.ifBlank { "نامشخص" }}"
        detailView.findViewById<TextView>(R.id.detailPostalCode).text =
            record.postalCode.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailAddress).text =
            record.address.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailNotes).text =
            record.notes.ifBlank { "ندارد" }
        detailView.findViewById<TextView>(R.id.detailRegisteredAt).text =
            record.registeredAt.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailSeller).text =
            record.seller.ifBlank { "نامشخص" }
        detailView.findViewById<TextView>(R.id.detailDeliveryStatus).text =
            normalizeDeliveryStatus(record.deliveryStatus).ifBlank { "نامشخص" }
        val notesInput = detailView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.detailOperatorNotes
        )
        notesInput.setText(record.operatorNotes)
        val statusView = detailView.findViewById<TextView>(R.id.detailStatus)
        statusView.text = record.status.label
        statusView.backgroundTintList = ContextCompat.getColorStateList(
            this,
            when (record.status) {
                RecordStatus.PENDING -> R.color.patra_status_pending
                RecordStatus.ACCEPTED -> R.color.patra_status_accepted
                RecordStatus.REJECTED -> R.color.patra_status_rejected
            }
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("جزئیات مشتری")
            .setView(detailView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        detailView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonAccept)
            .setOnClickListener {
                updateRecordStatus(record, RecordStatus.ACCEPTED)
                dialog.dismiss()
            }
        detailView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonPending)
            .setOnClickListener {
                updateRecordStatus(record, RecordStatus.PENDING)
                dialog.dismiss()
            }
        detailView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonReject)
            .setOnClickListener {
                updateRecordStatus(record, RecordStatus.REJECTED)
                dialog.dismiss()
            }
        detailView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonCall)
            .setOnClickListener {
                showCallOptions(record)
            }
        detailView.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonSaveNotes)
            .setOnClickListener {
                val updatedNotes = notesInput.text?.toString()?.trim().orEmpty()
                updateRecordNotes(record, updatedNotes)
                dialog.dismiss()
            }

        dialog.show()
    }

    private fun updateRecordStatus(record: CustomerRecord, status: RecordStatus) {
        val updated = recordStore.updateStatus(records, record.key(), status)
        records.clear()
        records.addAll(updated)
        showRecords(currentFilter)
        Toast.makeText(this, "وضعیت به ${status.label} منتقل شد.", Toast.LENGTH_SHORT).show()
    }

    private fun updateRecordNotes(record: CustomerRecord, notes: String) {
        val updated = recordStore.updateOperatorNotes(records, record.key(), notes)
        records.clear()
        records.addAll(updated)
        showRecords(currentFilter)
        Toast.makeText(this, "توضیحات ذخیره شد.", Toast.LENGTH_SHORT).show()
    }

    private fun normalizeDeliveryStatus(raw: String): String {
        val normalized = raw
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return when {
            normalized.contains("در انتظار") && normalized.contains("تحویل") -> "در انتظار تحویل"
            normalized.contains("وصولی") -> "وصولی"
            normalized.contains("کنسل") || normalized.contains("کنسلی") -> "کنسل نهایی"
            normalized.contains("انصرافی") -> "انصرافی هماهنگی"
            else -> normalized
        }
    }

    private fun showCallOptions(record: CustomerRecord) {
        val options = listOfNotNull(
            record.mobile.takeIf { it.isNotBlank() }?.let { "موبایل: $it" },
            record.phone.takeIf { it.isNotBlank() }?.let { "تلفن: $it" }
        )
        if (options.isEmpty()) {
            Toast.makeText(this, "شماره تماسی ثبت نشده است.", Toast.LENGTH_SHORT).show()
            return
        }
        if (options.size == 1) {
            dialNumber(options.first().substringAfter(":").trim())
            return
        }
        AlertDialog.Builder(this)
            .setTitle("انتخاب شماره تماس")
            .setItems(options.toTypedArray()) { _, which ->
                val number = options[which].substringAfter(":").trim()
                dialNumber(number)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivity(intent)
    }


    companion object {
        const val ACTION_OVERLAY_TAP = "ir.patraplus.webui.ACTION_OVERLAY_TAP"
        private const val PREFS_NAME = "records_prefs"
        private const val KEY_RECORDS = "records_json"
    }
}
