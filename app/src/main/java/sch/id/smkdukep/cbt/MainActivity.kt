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
    private val alertHandler = Handler(Looper.getMainLooper())
    
    // URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false
    private var isAlertShowing = false
    private var isLockTaskExiting = false
    private var isLockTaskActive = false // Lacak status lock task
    
    private var alertTimerRunnable: Runnable? = null
    private var lockTaskExitRunnable: Runnable? = null
    private var lockTaskStabilizerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inisialisasi DevicePolicyManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        
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
                stabilkanLockTask()
            }
        }
        
        webView.loadUrl(examUrl)
        stabilkanLockTask()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                showExitConfirmation()
                return true
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                // Langsung stabilkan tanpa toast
                stabilkanLockTask()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (!hasFocus && !isExiting && !isAlertShowing && !isLockTaskExiting) {
            detectLockTaskExit()
        }
        
        if (hasFocus && isLockTaskExiting) {
            cancelLockTaskExit()
        }
        
        // Stabilkan lock task setiap kali fokus berubah
        if (hasFocus && !isExiting && !isAlertShowing) {
            stabilkanLockTask()
        }
    }

    /**
     * Stabilkan lock task dengan pengecekan berkala
     */
    private fun stabilkanLockTask() {
        if (isExiting || isAlertShowing) return
        
        // Batalkan stabilizer sebelumnya
        lockTaskStabilizerRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Aktifkan lock task jika belum aktif
        if (!isLockTaskActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask()
                hideSystemUI()
                isLockTaskActive = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Cek secara berkala setiap 2 detik
        lockTaskStabilizerRunnable = Runnable {
            if (!isExiting && !isAlertShowing && !isLockTaskExiting) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        // Cek apakah lock task masih aktif
                        // Jika tidak, aktifkan lagi
                        startLockTask()
                        hideSystemUI()
                        isLockTaskActive = true
                    } catch (e: Exception) {
                        isLockTaskActive = false
                    }
                }
                // Jadwalkan pengecekan berikutnya
                mainHandler.postDelayed(lockTaskStabilizerRunnable!!, 2000)
            }
        }
        
        // Mulai pengecekan berkala
        mainHandler.postDelayed(lockTaskStabilizerRunnable!!, 2000)
    }

    private fun detectLockTaskExit() {
        isLockTaskExiting = true
        isLockTaskActive = false
        lockTaskExitRunnable?.let { mainHandler.removeCallbacks(it) }
        
        Toast.makeText(this, "⚠️ Akan logout dalam 3 detik", Toast.LENGTH_SHORT).show()
        
        lockTaskExitRunnable = Runnable {
            if (isLockTaskExiting) {
                performForceLogout()
            }
        }
        mainHandler.postDelayed(lockTaskExitRunnable!!, 3000)
    }

    private fun cancelLockTaskExit() {
        if (isLockTaskExiting) {
            isLockTaskExiting = false
            lockTaskExitRunnable?.let { mainHandler.removeCallbacks(it) }
            stabilkanLockTask()
        }
    }

    private fun performForceLogout() {
        if (isExiting) return
        
        isExiting = true
        isLockTaskExiting = false
        isAlertShowing = false
        isLockTaskActive = false
        
        Toast.makeText(this, "⚠️ Logout otomatis", Toast.LENGTH_SHORT).show()
        
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

    private fun showExitConfirmation() {
        if (isAlertShowing || isLockTaskExiting) return
        
        isAlertShowing = true
        isLockTaskActive = false
        
        // Matikan stabilizer sementara
        lockTaskStabilizerRunnable?.let { mainHandler.removeCallbacks(it) }
        
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
                stabilkanLockTask() // Aktifkan kembali stabilizer
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        alertTimerRunnable = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                isAlertShowing = false
                stabilkanLockTask() // Aktifkan kembali stabilizer
            }
        }
        
        alertHandler.postDelayed(alertTimerRunnable!!, 1000)
    }

    private fun performLogout() {
        isExiting = true
        isAlertShowing = false
        isLockTaskExiting = false
        isLockTaskActive = false
        
        // Matikan semua stabilizer
        lockTaskStabilizerRunnable?.let { mainHandler.removeCallbacks(it) }
        
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
        stabilkanLockTask() // Panggil stabilizer
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
