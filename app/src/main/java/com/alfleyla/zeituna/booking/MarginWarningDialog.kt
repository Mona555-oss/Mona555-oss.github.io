package com.alfleyla.zeituna.booking

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.alfleyla.zeituna.R
import com.google.android.material.button.MaterialButton

class MarginWarningDialog : DialogFragment() {

    private val TERMS_URL = "https://mona555-oss.github.io/terms_of_service.html"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_margin_warning, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMessage = view.findViewById<TextView>(R.id.tvMarginWarningMessage)
        val btnOk = view.findViewById<MaterialButton>(R.id.btnMarginWarningOk)

        setupTermsLink(tvMessage)

        btnOk.setOnClickListener {
            dismiss()
        }
    }

    private fun setupTermsLink(textView: TextView) {
        val text = textView.text.toString()
        val spannable = SpannableString(text)
        val target = "terms of service"
        val start = text.lowercase().indexOf(target)

        if (start != -1) {
            val end = start + target.length
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    val intent = CustomTabsIntent.Builder().build()
                    intent.launchUrl(requireContext(), Uri.parse(TERMS_URL))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(requireContext(), R.color.link_blue)
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
