package com.gmail.spittelermattijn.sipkip

internal object Constants {
    // values have to be globally unique
    const val INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect"
    const val NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel"
    const val INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity"

    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001
    const val BLUETOOTH_DEVICE_NAME = "SipKip"
    const val BLUETOOTH_GET_DEVICE_FILES_DELAY = 7 // s
    const val BLUETOOTH_COMMAND_TIMEOUT = 1000 // ms
}