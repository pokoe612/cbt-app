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
import android.os.Handler
import android.os.Looper
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
    private var isOnline = true  // 🔥 TAMBAHAN: status koneksi
    private val mainHandler = Handler(Looper.getMainLooper())  // 🔥 TAMBAHAN

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

        // 🔥 TAMBAHAN: Cek internet dulu sebelum load
        checkInternetAndLoad()

        hideSystemUI()

        startKioskMode()

        checkLockTaskActive()
    }

    /**
     * 🔥 TAMBAHAN: Cek koneksi internet
     */
    private fun isNetworkAvailable(): Boolean {
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
    }

    /**
     * 🔥 TAMBAHAN: Cek internet lalu load URL
     */
    private fun checkInternetAndLoad() {
        if (isNetworkAvailable()) {
            isOnline = true
            webView.loadUrl(examUrl)
        } else {
            isOnline = false
            showNoInternetPage()
        }
    }

    /**
     * 🔥 TAMBAHAN: Tampilkan halaman no internet
     */
    private fun showNoInternetPage() {
        val noInternetHtml = """
           <!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tidak Ada Koneksi</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: white;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
            padding: 20px;
        }
        .container {
            text-align: center;
            padding: 40px 30px;
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            max-width: 90%;
            width: 350px;
        }
        .icon {
            font-size: 70px;
            margin-bottom: 20px;
        }
        h1 {
            color: #333;
            margin-bottom: 12px;
            font-size: 24px;
        }
        p {
            color: #666;
            margin-bottom: 25px;
            line-height: 1.5;
        }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 12px 30px;
            font-size: 16px;
            border-radius: 30px;
            cursor: pointer;
            font-weight: 500;
        }
        button:hover {
            opacity: 0.9;
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
        
        webView.loadDataWithBaseURL(null, noInternetHtml, "text/html", "UTF-8", null)
        Toast.makeText(this, "⚠️ Tidak ada koneksi internet", Toast.LENGTH_LONG).show()
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

            cacheMode = WebSettings.LOAD_DEFAULT  // 🔥 UBAH ke DEFAULT

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

            // 🔥 TAMBAHAN: Tangani error koneksi
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (!isNetworkAvailable()) {
                    showNoInternetPage()
                }
            }

            @Deprecated("Deprecated")
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
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
            // 🔥 TAMBAHAN: Cek ulang koneksi saat aplikasi mendapat fokus
            if (!isOnline && isNetworkAvailable()) {
                isOnline = true
                webView.loadUrl(examUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // jika siswa menolak sematkan → aplikasi keluar
        checkLockTaskActive()
        
        // 🔥 TAMBAHAN: Cek koneksi saat resume
        if (!isNetworkAvailable()) {
            showNoInternetPage()
        }
    }

    /**
     * Tombol BACK
     */
    override fun onBackPressed() {
        // 🔥 TAMBAHAN: Jika offline, reload untuk cek koneksi
        if (!isNetworkAvailable()) {
            checkInternetAndLoad()
            return
        }
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

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)  // 🔥 TAMBAHAN
    }
}
