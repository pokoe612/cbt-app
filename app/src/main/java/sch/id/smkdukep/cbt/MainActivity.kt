package sch.id.smkdukep.cbt

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val alertHandler = Handler(Looper.getMainLooper())
    
    // URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false
    private var isAlertShowing = false
    private var alertTimerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup WebView
        webView = findViewById(R.id.webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isAlertShowing && !isExiting) {
                    enableLockTask()
                }
            }
        }
        
        webView.loadUrl(examUrl)
        enableLockTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                } else {
                    showExitConfirmation()
                    return true
                }
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                if (!isAlertShowing) {
                    Toast.makeText(this, "⚠️ Mode Ujian Aktif", Toast.LENGTH_SHORT).show()
                    mainHandler.postDelayed({
                        enableLockTask()
                    }, 100)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isExiting && !isAlertShowing) {
            mainHandler.postDelayed({
                if (!isExiting && !isAlertShowing) {
                    enableLockTask()
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            }, 200)
        }
    }

    /**
     * Menampilkan konfirmasi keluar selama 1 DETIK
     */
    private fun showExitConfirmation() {
        if (isAlertShowing) return
        
        isAlertShowing = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.\n\n⏱️ Alert akan otomatis tertutup dalam 1 DETIK jika tidak ada pilihan.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
                isAlertShowing = false
                performLogout()
            }
            .setNegativeButton("Batal") { _, _ ->
                alertTimerRunnable?.let { alertHandler.removeCallbacks(it) }
                isAlertShowing = false
                Toast.makeText(this, "✅ Ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                reactivateLockTask()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Timer 1 DETIK untuk auto close
        alertTimerRunnable = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                isAlertShowing = false
                Toast.makeText(this, "⏱️ Waktu habis, ujian dilanjutkan", Toast.LENGTH_SHORT).show()
                reactivateLockTask()
            }
        }
        
        alertHandler.postDelayed(alertTimerRunnable!!, 1000) // 1000ms = 1 detik
    }

    private fun reactivateLockTask() {
        mainHandler.postDelayed({
            if (!isExiting && !isAlertShowing) {
                enableLockTask()
                Log.d("LockTask", "Lock task diaktifkan kembali")
            }
        }, 300)
    }

    private fun performLogout() {
        isExiting = true
        isAlertShowing = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        webView.loadUrl(logoutUrl)
        
        mainHandler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.clearHistory()
                webView.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            }
            finishAffinity()
        }, 1500)
    }

    private fun enableLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isExiting && !isAlertShowing) {
            try {
                startLockTask()
                hideSystemUI()
                Log.d("LockTask", "Lock task aktif")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        alertHandler.removeCallbacksAndMessages(null)
    }
}
