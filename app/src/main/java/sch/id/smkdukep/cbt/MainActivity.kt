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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    // ⚙️ URL UJIAN
    private val examUrl = "https://smkdukep.sch.id/cbt/login"
    private val logoutUrl = "https://smkdukep.sch.id/cbt/logout"
    
    private var isExiting = false // Cegah multiple calls

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inisialisasi DevicePolicyManager
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        
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
                enableLockTask()
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
                Toast.makeText(this, "⚠️ Tidak bisa keluar dari mode ujian", Toast.LENGTH_LONG).show()
                // Re-lock task jika mencoba keluar
                handler.postDelayed({
                    enableLockTask()
                }, 100)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isExiting) {
            // Aplikasi kehilangan fokus (mungkin user coba keluar)
            handler.postDelayed({
                if (!isExiting) {
                    enableLockTask()
                    // Force kembali ke aplikasi
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            }, 200)
        }
    }

    override fun onTaskDescriptionChanged() {
        super.onTaskDescriptionChanged()
        // Deteksi perubahan task (user coba keluar)
        if (!isExiting) {
            enableLockTask()
        }
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Keluar")
            .setMessage("Anda yakin ingin keluar dari aplikasi? Ini akan mengakhiri sesi ujian Anda.")
            .setPositiveButton("Ya, Keluar") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Batal", null)
            .setCancelable(false)
            .show()
    }

    private fun performLogout() {
        isExiting = true
        
        // Matikan lock task
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }
        
        // Muat URL logout
        webView.loadUrl(logoutUrl)
        
        // Tunggu sebentar untuk proses logout
        handler.postDelayed({
            // Clear semua data WebView (opsional, untuk bersihkan session)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.clearHistory()
                webView.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
            }
            
            // Tutup aplikasi
            finishAffinity()
        }, 1500) // Delay 1.5 detik
    }

    private fun enableLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isExiting) {
            try {
                startLockTask()
                // Sembunyikan navigation bar dan status bar
                hideSystemUI()
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
        handler.removeCallbacksAndMessages(null)
    }
}
