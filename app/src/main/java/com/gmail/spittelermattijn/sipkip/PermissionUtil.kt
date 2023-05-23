package com.gmail.spittelermattijn.sipkip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import kotlin.reflect.KFunction0


object PermissionUtil {
    private val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH)
    else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH)
    else
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

    /**
     * Android 12 permission handling
     */
    private fun showRationaleDialog(activity: Activity, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.bluetooth_permission_title)
        builder.setMessage(R.string.bluetooth_permission_grant)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setPositiveButton(android.R.string.ok, listener)
        builder.show()
    }

    private fun showSettingsDialog(activity: Activity, settingsActivityResultLauncher: ActivityResultLauncher<Intent>) {
        @SuppressLint("DiscouragedApi") val s = activity.resources.getString(
            activity.resources.getIdentifier(
                "@android:string/${
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) "permgrouplab_location"
                    else "permgrouplab_nearby_devices"
                }",
                null,
                null
            )
        )
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(activity.getString(R.string.bluetooth_permission_title))
        builder.setMessage(activity.getString(R.string.bluetooth_permission_denied, s))
        builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, which: Int ->
            activity.finish()
        }
        @SuppressLint("DiscouragedApi") val id = activity.resources.getIdentifier(
            "@android:string/global_action_settings",
            null,
            null
        )
        builder.setPositiveButton(id) { dialog: DialogInterface?, which: Int ->
            settingsActivityResultLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                )
            )
        }
        builder.show()
    }

    fun hasPermissions(
        activity: Activity,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        return if (!permissionsGranted(activity)) {
            requestPermissionLauncher.launch(permissions)
            false
        } else {
            true
        }
    }

    fun permissionsGranted(activity: Activity) = permissions.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun onPermissionsResult(
        activity: Activity,
        granted: Map<String, Boolean>,
        cb: KFunction0<Unit>,
        settingsActivityResultLauncher: ActivityResultLauncher<Intent>
    ) {
        if (!granted.containsValue(false)) {
            cb()
        } else if (activity.shouldShowRequestPermissionRationale(permissions[0])) {
            showRationaleDialog(activity) { dialog: DialogInterface?, which: Int -> cb() }
        } else {
            showSettingsDialog(activity, settingsActivityResultLauncher)
        }
    }

    fun onSettingsActivityResult(activity: Activity) {
        if (!permissionsGranted(activity))
            activity.finish()
    }
}