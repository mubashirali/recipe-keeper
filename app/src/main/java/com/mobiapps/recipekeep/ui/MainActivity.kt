package com.mobiapps.recipekeep.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mobiapps.recipekeep.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var consentInformation: ConsentInformation
    private val isMobileAdsInitialized = AtomicBoolean(false)

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        gatherConsent()
    }

    /**
     * Targeting SDK 36 makes the app edge-to-edge on Android 15+, so content draws under the status
     * bar, navigation bar/gesture area and keyboard. Pad the whole nav host by those insets so the
     * toolbar, Save button, back arrow and the bottom ad banner stay visible and tappable.
     * On older Android versions the system already insets the window, so these values are 0.
     */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun gatherConsent() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.errorCode} - ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsIfNeeded()
                    }
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.errorCode} - ${error.message}")
            }
        )

        // Already consented on a previous launch — init ads immediately without waiting for the form.
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsIfNeeded()
        }
    }

    private fun initializeMobileAdsIfNeeded() {
        if (!isMobileAdsInitialized.getAndSet(true)) {
            MobileAds.initialize(this) {}
        }
    }
}
