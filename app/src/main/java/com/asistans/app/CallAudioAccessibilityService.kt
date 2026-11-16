package com.asistans.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Hiçbir ekran içeriğini okumaz. Tek amacı: Android, görüşme sırasında
 * mikrofonu yalnızca erişilebilirlik hizmeti olan uygulamalarla paylaşır.
 * Bu hizmet açıkken asistan arayanın sesini duyabilir.
 */
class CallAudioAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, CallAudioAccessibilityService::class.java)
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
