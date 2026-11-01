package com.aditya.powermonitor.util

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object PermissionUtils {

    /**
     * Special permissions like usage access are not "granted" through the normal permission
     * system, so checkSelfPermission() can't see them. Instead the OS tracks them as an
     * "app op" (AppOpsManager), and MODE_ALLOWED means the user switched it on.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens the Usage access settings page. Passing our package URI jumps straight to our
     * app's toggle on many phones; some manufacturers' Settings apps reject that, so we
     * fall back to the general list.
     */
    fun openUsageAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}
