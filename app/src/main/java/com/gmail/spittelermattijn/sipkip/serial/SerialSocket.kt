package com.gmail.spittelermattijn.sipkip.serial

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.gmail.spittelermattijn.sipkip.Constants
import java.util.UUID

class SerialSocket(context: Context, device: BluetoothDevice) {
    private val disconnectBroadcastReceiver: BroadcastReceiver
    private val context: Context
    private var listener: SerialListener? = null
    private val device: BluetoothDevice
    private var socket: BluetoothSocket? = null
    private var connected = false

    init {
        if (context is Activity) throw java.security.InvalidParameterException("expected non UI context")
        this.context = context
        this.device = device
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                listener?.onSerialIoError(java.io.IOException("background disconnect"))
                disconnect() // disconnect now, else would be queued until UI re-attached
            }
        }
    }

    @get:SuppressLint("MissingPermission")
    val name: String
        get() = if (device.name != null) device.name else device.address

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    fun connect(listener: SerialListener?) {
        this.listener = listener
        context.registerReceiver(
            disconnectBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_DISCONNECT)
        )
        val serialThread = Thread(::run)
        serialThread.start()
    }

    fun disconnect() {
        listener = null // ignore remaining data and errors
        // connected = false; // run loop will reset connected
        try {
            socket?.close()
        } catch (ignored: Exception) {
        }
        socket = null
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    @Throws(java.io.IOException::class)
    fun write(data: ByteArray?) {
        if (!connected) throw java.io.IOException("not connected")
        socket!!.outputStream.write(data)
    }

    @SuppressLint("MissingPermission")
    private fun run() { // connect & read
        try {
            socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            socket!!.connect()
            listener?.onSerialConnect()
        } catch (e: Exception) {
            listener?.onSerialConnectError(e)
            try {
                socket!!.close()
            } catch (ignored: Exception) {
            }
            socket = null
            return
        }
        connected = true
        try {
            val buffer = ByteArray(1024)
            var len: Int
            while (true) {
                len = socket!!.inputStream.read(buffer)
                val data = buffer.copyOf(len)
                listener?.onSerialRead(data)
            }
        } catch (e: Exception) {
            connected = false
            listener?.onSerialIoError(e)
            try {
                socket!!.close()
            } catch (ignored: Exception) {
            }
            socket = null
        }
    }

    companion object {
        val BLUETOOTH_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}