package com.alfleyla.zeituna.booking

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alfleyla.zeituna.R
import com.alfleyla.zeituna.profile.ProfileActivity
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarBookingActivity : AppCompatActivity() {

    private lateinit var viewModel: CalendarBookingViewModel
    private lateinit var calendarView: CalendarView
    private lateinit var rvTimeSlots: RecyclerView
    private lateinit var btnConfirm: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoSlots: TextView
    private lateinit var bookingContent: View
    private lateinit var layoutSuccess: View
    private lateinit var btnGoToProfile: Button

    private lateinit var layoutPurchaseTerms: View
    private lateinit var tvPurchasePolicyText: TextView
    private lateinit var cbAgreePurchase: CheckBox

    private var selectedDate: String = ""
    private var selectedTime: String? = null

    private var bookititServiceId: String? = null
    private var supabaseServiceId: String? = null
    private var bookititAgendaId: String? = null
    private var serviceName: String = ""
    private var servicePrice: Double = 0.0
    private var serviceDuration: Int = 30
    private var serviceCount: Int = 1

    // Logic Mode Flags
    private var isPrepaidFlow: Boolean = false
    private var existingBookingId: String? = null

    // Consistent Terms URL across platforms
    private val TERMS_URL = "https://mona555-oss.github.io/terms_of_service.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar_booking)

        bookititServiceId = intent.getStringExtra("BOOKITIT_SERVICE_ID")
        supabaseServiceId = intent.getStringExtra("SUPABASE_SERVICE_ID")
        bookititAgendaId = intent.getStringExtra("BOOKITIT_AGENDA_ID")
        serviceName = intent.getStringExtra("SERVICE_NAME") ?: "Lesson"
        servicePrice = intent.getDoubleExtra("SERVICE_PRICE", 0.0)
        serviceDuration = intent.getIntExtra("SERVICE_DURATION", 30)
        serviceCount = intent.getIntExtra("SERVICE_COUNT", 1)

        val isPackage = intent.getBooleanExtra("IS_PACKAGE_LESSON", false)
        val isReschedule = intent.getBooleanExtra("IS_RESCHEDULE", false)
        isPrepaidFlow = isPackage || isReschedule

        existingBookingId = intent.getStringExtra("EXISTING_BOOKING_ID")

        setupToolbar(if (isReschedule) "Reschedule Lesson" else serviceName)
        initViews()
        setupCalendar()
        setupViewModel()
        setupTermsText()

        // Handle possible deep link if activity is started by one
        checkDeepLink(intent)

        // Fetch fresh details from Supabase to ensure the price is correct
        supabaseServiceId?.let { viewModel.fetchServiceDetails(it) }
        bookititAgendaId?.let { viewModel.fetchAgendaConfig(it) }

        val today = Calendar.getInstance()
        updateSelectedDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))

        loadSlots()
    }

    private fun setupToolbar(title: String) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initViews() {
        calendarView = findViewById(R.id.calendarView)
        rvTimeSlots = findViewById(R.id.rvTimeSlots)
        btnConfirm = findViewById(R.id.btnConfirmBooking)
        progressBar = findViewById(R.id.pbLoadingSlots)
        tvNoSlots = findViewById(R.id.tvNoSlotsMessage)
        bookingContent = findViewById(R.id.bookingContent)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        btnGoToProfile = findViewById(R.id.btnGoToProfile)

        layoutPurchaseTerms = findViewById(R.id.layoutPurchaseTerms)
        tvPurchasePolicyText = findViewById(R.id.tvPurchasePolicyText)
        cbAgreePurchase = findViewById(R.id.cbAgreePurchase)

        rvTimeSlots.layoutManager = GridLayoutManager(this, 3)

        if (isPrepaidFlow) {
            btnConfirm.text = "Confirm Schedule"
            layoutPurchaseTerms.visibility = View.GONE
        } else {
            btnConfirm.text = "Pay with PayPal"
        }

        cbAgreePurchase.setOnCheckedChangeListener { _, _ -> validateBookingState() }

        btnConfirm.setOnClickListener {
            if (selectedTime == null) {
                Toast.makeText(this, "Please select a time slot first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Margin Check (20 hours)
            val marginCheck = viewModel.validateSchedulingMargin(selectedDate, selectedTime!!)
            if (marginCheck.isFailure) {
                showMarginWarningDialog()
                return@setOnClickListener
            }

            if (!isPrepaidFlow && layoutPurchaseTerms.visibility == View.VISIBLE && !cbAgreePurchase.isChecked) {
                Toast.makeText(this, "Please agree to the purchase terms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isPrepaidFlow && layoutPurchaseTerms.visibility == View.VISIBLE && cbAgreePurchase.isChecked) {
                viewModel.updatePurchaseAgreement()
            }

            if (isPrepaidFlow) {
                confirmPrepaidBooking()
            } else {
                openPayPalPayment()
            }
        }

        btnGoToProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showMarginWarningDialog() {
        MarginWarningDialog().show(supportFragmentManager, "margin_warning")
    }

    private fun setupTermsText() {
        val text = tvPurchasePolicyText.text.toString()
        val spannable = SpannableString(text)
        val target = "terms of service"
        val start = text.lowercase().indexOf(target)

        if (start != -1) {
            val end = start + target.length
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    val intent = CustomTabsIntent.Builder().build()
                    intent.launchUrl(this@CalendarBookingActivity, Uri.parse(TERMS_URL))
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@CalendarBookingActivity, R.color.link_blue)
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        tvPurchasePolicyText.text = spannable
        tvPurchasePolicyText.movementMethod = LinkMovementMethod.getInstance()
        tvPurchasePolicyText.highlightColor = Color.TRANSPARENT
    }

    private fun confirmPrepaidBooking() {
        val bId = bookititServiceId
        val bookingId = existingBookingId
        val time = selectedTime

        if (bId != null && bookingId != null && time != null) {
            viewModel.schedulePackageLesson(
                bookititId = bId,
                bookingId = bookingId,
                date = selectedDate,
                time = time,
                duration = serviceDuration
            )
        } else {
            Toast.makeText(this, "Error: Missing metadata for scheduling.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPayPalPayment() {
        val businessEmail = "mona@silentframes.net"
        val currentPrice = viewModel.serviceDetails.value?.price ?: servicePrice

        if (currentPrice <= 0) {
            Toast.makeText(this, "Invalid Price", Toast.LENGTH_SHORT).show()
            return
        }

        val returnUrl = "https://mona555-oss.github.io/index.html"
        val encodedReturn = Uri.encode(returnUrl)
        
        // Clean item name to avoid character encoding issues that trigger PayPal 400
        val safeItemName = serviceName.filter { it.isLetterOrDigit() || it.isWhitespace() }.take(20).trim()
        val encodedItem = Uri.encode(if (safeItemName.isEmpty()) "Lesson" else safeItemName)

        // Improved URL construction: Added charset and simplified formatting
        val paypalUrl = "https://www.paypal.com/cgi-bin/webscr?" +
                "cmd=_xclick" +
                "&business=${Uri.encode(businessEmail)}" +
                "&item_name=$encodedItem" +
                "&amount=${String.format(Locale.US, "%.2f", currentPrice)}" +
                "&currency_code=USD" +
                "&no_shipping=1" +
                "&rm=1" + 
                "&return=$encodedReturn" +
                "&charset=UTF-8"

        val builder = CustomTabsIntent.Builder()
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.turquoise_primary))
        
        val customTabsIntent = builder.build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(this, Uri.parse(paypalUrl))
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkDeepLink(intent)
    }

    private fun checkDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            val isSuccess = (data.scheme == "alfleyla" && data.host == "payment-success") ||
                            (data.scheme == "https" && data.host == "mona555-oss.github.io" && data.path?.contains("index.html") == true)

            if (isSuccess) {
                supabaseServiceId?.let { sId ->
                    bookititServiceId?.let { bId ->
                        selectedTime?.let { time ->
                            val service = viewModel.serviceDetails.value
                            val lessonsToGive = service?.count ?: serviceCount
                            val duration = service?.duration ?: serviceDuration
                            viewModel.confirmBooking(bId, sId, selectedDate, time, duration, lessonsToGive)
                        }
                    }
                }
            }
        }
    }

    private fun setupCalendar() {
        calendarView.minDate = System.currentTimeMillis() - 1000
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            updateSelectedDate(year, month, dayOfMonth)
            loadSlots()
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[CalendarBookingViewModel::class.java]

        viewModel.bookingStatus.observe(this) { result ->
            result?.onSuccess { showSuccessState() }
                ?.onFailure { e -> Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show() }
        }

        viewModel.serviceDetails.observe(this) { service ->
            if (service != null) {
                servicePrice = service.price
                serviceName = service.displayLabel
                serviceDuration = service.duration
                serviceCount = service.count
            }
        }

        fun updateTermsVisibility() {
            if (isPrepaidFlow) return
            val profile = viewModel.userProfile.value
            val hasPreviousPurchases = viewModel.hasPreviousPurchases.value ?: false
            val hasAgreed = profile?.agreed_to_purchase_terms == true
            layoutPurchaseTerms.visibility = if (hasAgreed || hasPreviousPurchases) View.GONE else View.VISIBLE
            validateBookingState()
        }

        viewModel.userProfile.observe(this) { updateTermsVisibility() }
        viewModel.hasPreviousPurchases.observe(this) { updateTermsVisibility() }

        viewModel.agendaConfig.observe(this) { config ->
            calendarView.minDate = config?.getMinBookingMillis() ?: System.currentTimeMillis()
            calendarView.maxDate = config?.getMaxBookingMillis() ?: (System.currentTimeMillis() + 31536000000L)
        }

        viewModel.slots.observe(this) { slots ->
            if (slots.isNullOrEmpty()) {
                rvTimeSlots.visibility = View.GONE
                tvNoSlots.visibility = View.VISIBLE
            } else {
                tvNoSlots.visibility = View.GONE
                rvTimeSlots.visibility = View.VISIBLE
                rvTimeSlots.adapter = TimeSlotAdapter(slots) { slot ->
                    selectedTime = slot.start.take(5)
                    validateBookingState()
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun validateBookingState() {
        val hasTime = selectedTime != null
        val needsAgreement = !isPrepaidFlow && layoutPurchaseTerms.visibility == View.VISIBLE
        val hasAgreement = if (needsAgreement) cbAgreePurchase.isChecked else true
        btnConfirm.isEnabled = hasTime && hasAgreement
    }

    private fun showSuccessState() {
        bookingContent.visibility = View.GONE
        btnConfirm.visibility = View.GONE
        layoutSuccess.visibility = View.VISIBLE
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = "Successful"
    }

    private fun updateSelectedDate(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, dayOfMonth)
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        selectedTime = null
        validateBookingState()
    }

    private fun loadSlots() {
        val bId = bookititServiceId
        val aId = bookititAgendaId
        if (bId != null && aId != null) {
            viewModel.fetchAvailability(bId, selectedDate, aId)
        }
    }
}
