package sch.id.smkdukep.cbt

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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

        // 🔥 KRUSIAL: JANGAN LOAD URL DULU, CEK INTERNET TERLEBIH DAHULU
        if (isNetworkAvailable()) {
            webView.loadUrl(examUrl)
        } else {
            // 🔥 TAMPILKAN HTML LANGSUNG, TANPA PERNAH LOAD URL
            showNoInternetPage()
        }

        hideSystemUI()
        startKioskMode()
        checkLockTaskActive()
    }

    /**
     * Cek koneksi internet
     */
    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                return networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
    }

    /**
     * Tampilkan halaman no internet
     */
    private fun showNoInternetPage() {
        val noInternetHtml = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                    }
                    .container {
                        text-align: center;
                        padding: 40px;
                        background: white;
                        border-radius: 20px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        max-width: 90%;
                        width: 350px;
                    }
                    .icon { font-size: 80px; margin-bottom: 20px; }
                    h1 { color: #333; margin-bottom: 10px; font-size: 24px; }
                    p { color: #666; margin-bottom: 20px; line-height: 1.6; }
                    button {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        padding: 12px 30px;
                        font-size: 16px;
                        border-radius: 30px;
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">🌐</div>
                    <h1>Tidak Ada Koneksi Internet</h1>
                    <p>Periksa kembali koneksi internet Anda dan pastikan Anda terhubung ke jaringan.</p>
                    <button onclick="location.reload()">Coba Lagi</button>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        // 🔥 KRUSIAL: loadDataWithBaseURL, BUKAN loadUrl
        webView.loadDataWithBaseURL(null, noInternetHtml, "text/html", "UTF-8", null)
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

            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

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

                // 🔥 CEK INTERNET SEBELUM LOAD URL
                if (!isNetworkAvailable()) {
                    showNoInternetPage()
                    return true
                }

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

            // 🔥 TANGANI ERROR - TAPI JANGAN TAMPILKAN ERROR PAGE DEFAULT
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                // JANGAN panggil super, biar tidak muncul error page default
                if (!isNetworkAvailable()) {
                    showNoInternetPage()
                }
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
                    .setTitle("Peringatan!")
                    .setMessage("Aplikasi harus disematkan untuk memulai ujian.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ ->
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
        
        // 🔥 Cek koneksi saat resume
        if (isNetworkAvailable()) {
            // Jika ada internet dan halaman sedang offline, reload
            if (webView.url == null || webView.url?.startsWith("data") == true) {
                webView.loadUrl(examUrl)
            }
        } else {
            showNoInternetPage()
        }
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
