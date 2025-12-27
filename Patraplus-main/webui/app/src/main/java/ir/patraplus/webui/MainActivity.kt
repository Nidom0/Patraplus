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
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recordsContainer: View
    private lateinit var recordsRecycler: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var recordsTitle: TextView
    private lateinit var filterAll: com.google.android.material.chip.Chip
    private lateinit var filterPending: com.google.android.material.chip.Chip
    private lateinit var filterAccepted: com.google.android.material.chip.Chip
    private lateinit var filterRejected: com.google.android.material.chip.Chip
    private lateinit var filterStatusSpinner: android.widget.Spinner
    private lateinit var filterFromDate: TextInputEditText
    private lateinit var filterToDate: TextInputEditText
    private lateinit var filterPanel: View
    private lateinit var filterApplyButton: MaterialButton
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
        filterAll = findViewById(R.id.filterAll)
        filterPending = findViewById(R.id.filterPending)
        filterAccepted = findViewById(R.id.filterAccepted)
        filterRejected = findViewById(R.id.filterRejected)
        filterStatusSpinner = findViewById(R.id.filterStatusSpinner)
        filterFromDate = findViewById(R.id.filterFromDate)
        filterToDate = findViewById(R.id.filterToDate)
        filterPanel = findViewById(R.id.filterPanel)
        filterApplyButton = findViewById(R.id.filterApplyButton)

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

        setupFilterChips()
        setupAdvancedFilters()
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
        val finalList = applyAdvancedFilters(filtered, status)
        recordAdapter.submitList(finalList) {
            recordsRecycler.scheduleLayoutAnimation()
        }
        emptyState.visibility = if (finalList.isEmpty()) View.VISIBLE else View.GONE
        recordsContainer.visibility = View.VISIBLE
        webView.visibility = View.GONE
        updateFilterSelection(status)
        navigationView.setCheckedItem(
            when (status) {
                null -> R.id.nav_all
                RecordStatus.PENDING -> R.id.nav_pending
                RecordStatus.ACCEPTED -> R.id.nav_accepted
                RecordStatus.REJECTED -> R.id.nav_rejected
            }
        )
        filterPanel.visibility = if (status == null) View.VISIBLE else View.GONE
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

        dialog.show()
    }

    private fun updateRecordStatus(record: CustomerRecord, status: RecordStatus) {
        val updated = recordStore.updateStatus(records, record.key(), status)
        records.clear()
        records.addAll(updated)
        showRecords(currentFilter)
        Toast.makeText(this, "وضعیت به ${status.label} منتقل شد.", Toast.LENGTH_SHORT).show()
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

    private fun setupFilterChips() {
        filterAll.setOnClickListener { showRecords() }
        filterPending.setOnClickListener { showRecords(RecordStatus.PENDING) }
        filterAccepted.setOnClickListener { showRecords(RecordStatus.ACCEPTED) }
        filterRejected.setOnClickListener { showRecords(RecordStatus.REJECTED) }
        updateFilterSelection(currentFilter)
    }

    private fun updateFilterSelection(status: RecordStatus?) {
        filterAll.isChecked = status == null
        filterPending.isChecked = status == RecordStatus.PENDING
        filterAccepted.isChecked = status == RecordStatus.ACCEPTED
        filterRejected.isChecked = status == RecordStatus.REJECTED
    }

    private fun setupAdvancedFilters() {
        val options = listOf(
            "همه وضعیت‌ها",
            "وصولی",
            "کنسل نهایی",
            "در انتظار تحویل",
            "انصرافی هماهنگی"
        )
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )
        filterStatusSpinner.adapter = adapter

        filterFromDate.setOnClickListener { showPersianDatePicker(filterFromDate) }
        filterToDate.setOnClickListener { showPersianDatePicker(filterToDate) }

        filterApplyButton.setOnClickListener {
            if (currentFilter == null) {
                showRecords()
            }
        }
    }

    private fun applyAdvancedFilters(
        source: List<CustomerRecord>,
        status: RecordStatus?
    ): List<CustomerRecord> {
        if (status != null) return source

        val selectedStatus = filterStatusSpinner.selectedItem?.toString()?.trim().orEmpty()
        val fromDate = parseDate(filterFromDate.text?.toString())
        val toDate = parseDate(filterToDate.text?.toString())

        return source.filter { record ->
            val matchesDelivery = selectedStatus == "همه وضعیت‌ها" ||
                normalizeDeliveryStatus(record.deliveryStatus) == selectedStatus
            val recordDate = parseDate(record.registeredAt)
            val matchesFrom = fromDate == null || (recordDate != null && !recordDate.before(fromDate))
            val matchesTo = toDate == null || (recordDate != null && !recordDate.after(toDate))
            matchesDelivery && matchesFrom && matchesTo
        }
    }

    private fun parseDate(value: String?): java.util.Date? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        val parts = text.split("-", "/")
        if (parts.size < 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null

        val gregorian = if (year > 1900) {
            Triple(year, month, day)
        } else {
            jalaliToGregorian(year, month, day)
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.isLenient = false
        return runCatching {
            formatter.parse(String.format(Locale.US, "%04d-%02d-%02d", gregorian.first, gregorian.second, gregorian.third))
        }.getOrNull()
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

    private fun showPersianDatePicker(target: TextInputEditText) {
        val today = Calendar.getInstance()
        val jalaliToday = gregorianToJalali(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )

        val view = layoutInflater.inflate(R.layout.dialog_persian_date_picker, null)
        val yearPicker = view.findViewById<android.widget.NumberPicker>(R.id.pickerYear)
        val monthPicker = view.findViewById<android.widget.NumberPicker>(R.id.pickerMonth)
        val dayPicker = view.findViewById<android.widget.NumberPicker>(R.id.pickerDay)

        yearPicker.minValue = 1390
        yearPicker.maxValue = 1500
        yearPicker.value = jalaliToday.jy

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = jalaliToday.jm

        fun updateDayPicker() {
            dayPicker.minValue = 1
            dayPicker.maxValue = daysInJalaliMonth(yearPicker.value, monthPicker.value)
            if (dayPicker.value > dayPicker.maxValue) {
                dayPicker.value = dayPicker.maxValue
            }
        }

        dayPicker.value = jalaliToday.jd
        updateDayPicker()

        monthPicker.setOnValueChangedListener { _, _, _ -> updateDayPicker() }
        yearPicker.setOnValueChangedListener { _, _, _ -> updateDayPicker() }

        AlertDialog.Builder(this)
            .setTitle("انتخاب تاریخ")
            .setView(view)
            .setPositiveButton("ثبت") { _, _ ->
                val formatted = String.format(
                    Locale.US,
                    "%04d/%02d/%02d",
                    yearPicker.value,
                    monthPicker.value,
                    dayPicker.value
                )
                target.setText(formatted)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private data class JalaliDate(val jy: Int, val jm: Int, val jd: Int)

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = gy - 1600
        var gm2 = gm - 1
        var gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        gDayNo += gDays[gm2] + gd2
        if (gm2 > 1 && isGregorianLeap(gy)) {
            gDayNo++
        }
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (jDayNo < 186) {
            jm = 1 + jDayNo / 31
            jd = 1 + jDayNo % 31
        } else {
            jm = 7 + (jDayNo - 186) / 30
            jd = 1 + (jDayNo - 186) % 30
        }
        return JalaliDate(jy, jm, jd)
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var jy2 = jy - 979
        var jm2 = jm - 1
        var jd2 = jd - 1
        var jDayNo = 365 * jy2 + jy2 / 33 * 8 + (jy2 % 33 + 3) / 4
        jDayNo += if (jm2 < 6) jm2 * 31 else jm2 * 30 + 6 * 31 - 6 * 30
        jDayNo += jd2
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        val gd = gDayNo + 1
        val salA = intArrayOf(
            0,
            31,
            if (leap) 29 else 28,
            31,
            30,
            31,
            30,
            31,
            31,
            30,
            31,
            30,
            31
        )
        var gm = 0
        var day = gd
        for (i in 1..12) {
            if (day <= salA[i]) {
                gm = i
                break
            }
            day -= salA[i]
        }
        return Triple(gy, gm, day)
    }

    private fun isGregorianLeap(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun daysInJalaliMonth(jy: Int, jm: Int): Int {
        return when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            else -> if (isJalaliLeap(jy)) 30 else 29
        }
    }

    private fun isJalaliLeap(jy: Int): Boolean {
        val mod = ((jy - 474) % 2820) + 474
        return (((mod + 38) * 682) % 2816) < 682
    }

    companion object {
        const val ACTION_OVERLAY_TAP = "ir.patraplus.webui.ACTION_OVERLAY_TAP"
        private const val PREFS_NAME = "records_prefs"
        private const val KEY_RECORDS = "records_json"
    }
}
