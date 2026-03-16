package sch.id.smkdukep.cbt

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
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
    private val allowedDomain = "smkdukep.sch.id"

    private var isExiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔒 Blok Screenshot
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        adminComponent = ComponentName(this, AdminReceiver::class.java)

        setupWebView()

        webView.loadUrl(examUrl)

        hideSystemUI()

        startKioskMode()

        checkLockTaskActive()
    }

    /**
     * Setup WebView CBT
     */
    private fun setupWebView() {

        webView.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true

            allowFileAccess = true

            useWideViewPort = true
            loadWithOverviewMode = true

            cacheMode = WebSettings.LOAD_NO_CACHE

            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // 🔒 Blok copy soal
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {

                if (url == null) return false

                // 🔒 hanya domain sekolah
                if (!url.contains(allowedDomain)) {
                    return true
                }

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
     * 🔒 Aktifkan kiosk mode
     */
    private fun startKioskMode() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            try {

                if (devicePolicyManager.isDeviceOwnerApp(packageName)) {

                    devicePolicyManager.setLockTaskPackages(
                        adminComponent,
                        arrayOf(packageName)
                    )
                }

                startLockTask()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 🔒 Cek apakah aplikasi benar-benar disematkan
     */
    private fun checkLockTaskActive() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val activityManager =
                getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {

                AlertDialog.Builder(this)
                    .setTitle("Mode Ujian Belum Aktif")
                    .setMessage("Aplikasi harus disematkan untuk memulai ujian.")
                    .setCancelable(false)
                    .setPositiveButton("Tutup Aplikasi") { _, _ ->
                        finishAffinity()
                    }
                    .show()
            }
        }
    }

    /**
     * 🔒 Fullscreen
     */
    private fun hideSystemUI() {

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()

        // jika siswa menolak sematkan → aplikasi keluar
        checkLockTaskActive()
    }

    /**
     * Tombol BACK
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
}
