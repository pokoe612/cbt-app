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

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // 🔥 Delay agar koneksi stabil
        mainHandler.postDelayed({
            Thread {
                val isConnected = isInternetReallyAvailable()

                runOnUiThread {
                    if (isConnected) {
                        webView.loadUrl(examUrl)
                    } else {
                        showNoInternetPage()
                    }
                }
            }.start()
        }, 1500)

        hideSystemUI()
        startKioskMode()
        checkLockTaskActive()
    }

    /**
     * Cek jaringan (fallback)
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        )
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 🔥 Cek internet REAL
     */
    private fun isInternetReallyAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 smkdukep.sch.id")
            val returnVal = process.waitFor()
            returnVal == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Halaman offline
     */
    private fun showNoInternetPage() {
        val noInternetHtml = """
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
body {
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    background:#ffffff;
    display:flex;
    justify-content:center;
    align-items:center;
    height:100vh;
}
.container {
    text-align:center;
    padding:40px;
    border-radius:20px;
    box-shadow:0 10px 30px rgba(0,0,0,0.1);
    width:350px;
}
.icon { font-size:70px; margin-bottom:20px; }
h1 { color:#333; margin-bottom:10px; font-size:22px; }
p { color:#666; margin-bottom:20px; }
button {
    background:#333;
    color:#fff;
    border:none;
    padding:12px 30px;
    border-radius:25px;
}
</style>
</head>
<body>
<div class="container">
<div class="icon">🌐</div>
<h1>Tidak Ada Koneksi Internet</h1>
<p>Periksa kembali koneksi internet Anda.</p>
<button onclick="Android.logout()">Lepas sematan</button>
</div>
</body>
</html>
""".trimIndent()

        webView.loadDataWithBaseURL(null, noInternetHtml, "text/html", "UTF-8", null)
        Toast.makeText(this, "⚠️ Tidak ada koneksi internet", Toast.LENGTH_LONG).show()
    }

    /**
     * Setup WebView
     */
    private fun setupWebView() {

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.isLongClickable = false
        webView.setOnLongClickListener { true }

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun logout() {
                runOnUiThread {
                    performLogout()
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {

                if (url == null) return false

                if (!url.contains(allowedDomain)) return true

                if (url.contains("logout")) {
                    performLogout()
                    return true
                }

                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                view?.loadUrl(
                    "javascript:(function(){var e=document.querySelectorAll('[class*=error],[id*=error]');for(var i=0;i<e.length;i++){if(e[i].innerText.indexOf('net::ERR')>-1){e[i].style.display='none';}}})()"
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showNoInternetPage()
                }
            }
        }
    }

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

    private fun checkLockTaskActive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityManager =
                getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                AlertDialog.Builder(this)
                    .setTitle("Peringatan!")
                    .setMessage("Aplikasi harus disematkan untuk memulai ujian.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> finishAffinity() }
                    .show()
            }
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun onResume() {
        super.onResume()

        checkLockTaskActive()

        Thread {
            val isConnected = isInternetReallyAvailable()

            runOnUiThread {
                if (isConnected) {
                    if (webView.url == null || webView.url?.startsWith("data") == true) {
                        webView.loadUrl(examUrl)
                    }
                } else {
                    showNoInternetPage()
                }
            }
        }.start()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari ujian?")
            .setPositiveButton("Ya") { _, _ -> performLogout() }
            .setNegativeButton("Batal", null)
            .setCancelable(false)
            .show()
    }

    private fun performLogout() {

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
