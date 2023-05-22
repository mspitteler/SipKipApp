package com.gmail.spittelermattijn.sipkip.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/**
 * sort by name, then address. sort named devices first
 */
@SuppressLint("MissingPermission")
fun BluetoothDevice.compareTo(other: BluetoothDevice): Int {
    val thisValid = !name.isNullOrEmpty()
    val otherValid = !other.name.isNullOrEmpty()
    if (thisValid && otherValid) {
        val ret = name.compareTo(other.name)
        return if (ret != 0) ret else address.compareTo(other.address)
    }
    if (thisValid) return -1
    return if (otherValid) +1 else address.compareTo(other.address)
}