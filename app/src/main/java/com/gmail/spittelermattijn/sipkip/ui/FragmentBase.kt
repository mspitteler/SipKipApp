package com.gmail.spittelermattijn.sipkip.ui

import android.bluetooth.BluetoothDevice
import androidx.fragment.app.Fragment

abstract class FragmentBase : Fragment() {
    abstract fun onBluetoothDeviceFound(device: BluetoothDevice)
}