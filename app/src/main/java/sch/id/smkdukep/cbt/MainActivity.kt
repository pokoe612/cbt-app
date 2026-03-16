package sch.id.smkdukep.cbt

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false
    private var isAlertShowing = false
    private var lockCheckRunnable: Runnable? = null

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
                // Aktifkan lock task sekali saja
                enableLockTaskOnce()
            }
        }
        
        webView.loadUrl(examUrl)
        enableLockTaskOnce()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                showExitConfirmation()
                return true
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                // Tidak perlu melakukan apa-apa, lock task sudah aktif
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        // Hanya cek lock task jika aplikasi mendapat fokus dan tidak dalam keadaan exiting
        if (hasFocus && !isExiting && !isAlertShowing) {
            // Cek sekali, tanpa loop terus menerus
            mainHandler.postDelayed({
                checkLockTask()
            }, 500)
        }
    }

    /**
     * Aktifkan lock task sekali saja
     */
    private fun enableLockTaskOnce() {
        if (isExiting || isAlertShowing) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask()
                hideSystemUI()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Cek status lock task tanpa loop mengganggu
     */
    private fun checkLockTask() {
        if (isExiting || isAlertShowing) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Coba aktifkan lagi, jika sudah aktif tidak akan muncul notifikasi
                startLockTask()
                hideSystemUI()
            } catch (e: Exception) {
                // Abaikan error
            }
        }
    }

    /**
     * Menampilkan konfirmasi keluar
     */
    private fun showExitConfirmation() {
        if (isAlertShowing) return
        
        isAlertShowing = true
        
        // Matikan lock task sementara agar alert bisa muncul
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                stopLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                isAlertShowing = false
                performLogout()
            }
            .setNegativeButton("Batal") { _, _ ->
                isAlertShowing = false
                // Aktifkan lock task kembali
                enableLockTaskOnce()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Tidak ada timer auto close
    }

    /**
     * Proses logout
     */
    private fun performLogout() {
        isExiting = true
        isAlertShowing = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                stopLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
    }
}
