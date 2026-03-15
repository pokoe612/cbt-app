package sch.id.smkdukep.cbt

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Mode Ujian Aktif", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Mode Ujian Dinonaktifkan", Toast.LENGTH_SHORT).show()
    }
}
