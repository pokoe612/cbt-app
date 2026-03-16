package sch.id.smkdukep.cbt

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"

    private var isExiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anti Screenshot
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        adminComponent = ComponentName(this, AdminReceiver::class.java)

        webView = findViewById(R.id.webView)

        setupWebView()

        webView.loadUrl(examUrl)

        hideSystemUI()

        startKioskMode()
    }

    private fun setupWebView() {

        webView.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true

            useWideViewPort = true
            loadWithOverviewMode = true

            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {

                if (url == null) return false

                if (url.contains("logout")) {
                    performLogout()
                    return true
                }

                view?.loadUrl(url)
                return true
            }
        }
    }

    /**
     * Mode kiosk (lock task)
     */
    private fun startKioskMode() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            try {

                if (devicePolicyManager.isDeviceOwnerApp(packageName)) {

                    devicePolicyManager.setLockTaskPackages(
                        adminComponent,
                        arrayOf(packageName)
                    )

                    startLockTask()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Fullscreen immersive
     */
    private fun hideSystemUI() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    /**
     * Blok tombol Home / Recent
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH
        ) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * Tombol back
     */
    override fun onBackPressed() {
        showExitConfirmation()
    }

    /**
     * Konfirmasi keluar
     */
    private fun showExitConfirmation() {

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari ujian?")
            .setPositiveButton("Ya") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Batal", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Logout CBT
     */
    private fun performLogout() {

        isExiting = true

        webView.loadUrl(logoutUrl)

        webView.clearHistory()
        webView.clearCache(true)

        CookieManager.getInstance().removeAllCookies(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }

        finishAffinity()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemUI()
        }
    }
}
