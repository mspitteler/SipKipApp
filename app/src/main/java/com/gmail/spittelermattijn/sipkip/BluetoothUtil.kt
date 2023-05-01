package com.gmail.spittelermattijn.sipkip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import kotlin.reflect.KFunction0

object BluetoothUtil {
    private val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH)
    else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH)
    else
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

    /**
     * sort by name, then address. sort named devices first
     */
    @SuppressLint("MissingPermission")
    fun compareTo(a: BluetoothDevice, b: BluetoothDevice): Int {
        val aValid = a.name?.isNotEmpty() ?: false
        val bValid = b.name?.isNotEmpty() ?: false
        if (aValid && bValid) {
            val ret = a.name.compareTo(b.name)
            return if (ret != 0) ret else a.address.compareTo(b.address)
        }
        if (aValid) return -1
        return if (bValid) +1 else a.address.compareTo(b.address)
    }

    /**
     * Android 12 permission handling
     */
    private fun showRationaleDialog(activity: Activity, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(activity.getString(R.string.bluetooth_permission_title))
        builder.setMessage(activity.getString(R.string.bluetooth_permission_grant))
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Continue", listener)
        builder.show()
    }

    private fun showSettingsDialog(activity: Activity) {
        @SuppressLint("DiscouragedApi") val s = activity.resources.getString(
            activity.resources.getIdentifier(
                "@android:string/" + if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) "permgrouplab_location"
                else "permgrouplab_nearby_devices",
                null,
                null
            )
        )
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(activity.getString(R.string.bluetooth_permission_title))
        builder.setMessage(
            String.format(
                activity.getString(R.string.bluetooth_permission_denied),
                s
            )
        )
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Settings") { dialog: DialogInterface?, which: Int ->
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                )
            )
        }
        builder.show()
    }

    fun hasPermissions(
        activity: Activity,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        val missingPermissions =
            permissions.any { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        return if (missingPermissions) {
            requestPermissionLauncher.launch(permissions)
            false
        } else {
            true
        }
    }

    fun onPermissionsResult(activity: Activity, granted: Map<String, Boolean>, cb: KFunction0<Unit>) {
        if (!granted.containsValue(false)) {
            cb()
        } else if (activity.shouldShowRequestPermissionRationale(permissions[0])) {
            showRationaleDialog(activity) { dialog: DialogInterface?, which: Int -> cb() }
        } else {
            showSettingsDialog(activity)
        }
    }
}