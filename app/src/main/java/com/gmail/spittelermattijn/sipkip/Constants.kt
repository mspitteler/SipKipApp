package com.gmail.spittelermattijn.sipkip

internal object Constants {
    // values have to be globally unique
    const val INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect"
    const val NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel"
    const val INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity"

    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001

    const val DEFAULT_BLUETOOTH_DEVICE_NAME = "SipKip"
    const val DEFAULT_BLUETOOTH_COMMAND_TIMEOUT = 500 // ms
    const val DEFAULT_XMODEM_1K_THRESHOLD = 8192
    const val DEFAULT_ENCODER_SAMPLE_RATE = 48000
    const val DEFAULT_ENCODER_CHANNEL_COUNT = 1
    const val DEFAULT_ENCODER_BIT_RATE = 16000
    const val DEFAULT_ENCODER_FRAME_SIZE = DEFAULT_ENCODER_SAMPLE_RATE * 60 / 1000 // 60 ms frames
}