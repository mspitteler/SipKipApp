package com.gmail.spittelermattijn.sipkip

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.gmail.spittelermattijn.sipkip.databinding.ActivityFindDeviceBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class FindDeviceActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityFindDeviceBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val bondedBluetoothDevices: ArrayList<BluetoothDevice?> = ArrayList()
    private lateinit var requestBluetoothPermissionLauncherForRefresh: ActivityResultLauncher<Array<String>>
    private var hasPermissions = false
    private lateinit var deviceName: String
    private var bondedBluetoothDevice: BluetoothDevice? = null
        @SuppressLint("MissingPermission")
        set(device) {
            field = device
            // Check if the device is in range.
            field?.let {
                val socket = it.createRfcommSocketToServiceRecord(SerialSocket.BLUETOOTH_SPP)
                coroutineScope.launch {
                    try {
                        socket.connect()
                        socket.close()
                        bluetoothDevice = bondedBluetoothDevice
                    } catch (e: Exception) {
                        // Recursively call this setter.
                        bondedBluetoothDevice = bondedBluetoothDevice
                    }
                }
            }
        }
    private var bluetoothDevice: BluetoothDevice? = null
        set(device) {
            field = device
            field?.let { dev ->
                val intent = Intent(this, MainActivity::class.java).also {
                    it.putExtra("android.bluetooth.BluetoothDevice", dev)
                }
                startActivity(intent)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Preferences.getContext = { this }
        val frameSize: Float = Preferences[R.string.encoder_frame_size_key]
        println("frameSize: $frameSize")
        val timeout: Int = Preferences[R.string.bluetooth_command_timeout_key]
        println("timeout: $timeout")
        val longTimeout: Int = Preferences[R.string.bluetooth_command_long_timeout_key]
        println("longTimeout: $longTimeout")
        deviceName = Preferences[R.string.bluetooth_device_name_key]
        println("deviceName: $deviceName")
        val `1kThreshold`: Int = Preferences[R.string.xmodem_1k_threshold_key]
        println("1kThreshold: $`1kThreshold`")
        val useCrc: Boolean = Preferences[R.string.xmodem_use_crc_key]
        println("useCrc: $useCrc")
        val sampleRate: Int = Preferences[R.string.encoder_sample_rate_key]
        println("sampleRate: $sampleRate")
        val bitrate: Int = Preferences[R.string.encoder_bitrate_key]
        println("bitrate: $bitrate")
        val stereo: Boolean = Preferences[R.string.encoder_stereo_key]
        println("stereo: $stereo")

        super.onCreate(savedInstanceState)
        // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
        val settingsActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            BluetoothUtil.onSettingsActivityResult(this)
            refresh()
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        requestBluetoothPermissionLauncherForRefresh = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted: Map<String, Boolean>? ->
            BluetoothUtil.onPermissionsResult(this, granted!!, this::refresh, settingsActivityResultLauncher)
        }

        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.also { it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
            .also { it.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
            .addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)

        registerReceiver(receiver, filter)

        binding = ActivityFindDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarFindDevice.toolbar)

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_find_device) as NavHostFragment?)!!
        navHostFragment.view?.visibility = View.GONE
        val navController = navHostFragment.navController

        if (binding.navView != null) {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_find_device, R.id.nav_preferences, R.id.nav_bt_settings
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            binding.navView!!.setupWithNavController(navController)
        } else {
            supportActionBar?.title = getString(R.string.menu_find_device)
        }

        refresh()
        Preferences.registerOnChangeListener { key, `val` ->
            if (key == R.string.bluetooth_device_name_key) {
                // TODO: Do this a more elegant way.
                startActivity( Intent(this, FindDeviceActivity::class.java))
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        // Using findViewById because NavigationView exists in different layout files
        // between w600dp and w1240dp
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            // The navigation drawer already has the items including the items in the overflow menu
            // We only inflate the overflow menu if the navigation drawer isn't visible
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_bt_settings -> {
                val intent = Intent()
                intent.action = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                startActivity(intent)
            }
            R.id.nav_preferences -> {
                val navController = findNavController(R.id.nav_host_fragment_content_find_device)
                navController.navigate(R.id.nav_preferences)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_find_device)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)

        if (BluetoothUtil.permissionsGranted(this) &&
            bluetoothAdapter.isDiscovering)
            bluetoothAdapter.cancelDiscovery()
        super.onDestroy()
        if (Preferences.getContext() == this)
            Preferences.getContext = { null }
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        bondedBluetoothDevices.clear()
        hasPermissions = BluetoothUtil.hasPermissions(this, requestBluetoothPermissionLauncherForRefresh)
        // The code in this if statement should only be executed once, no matter what the user does while granting permissions.
        if (hasPermissions) {
            for (device in bluetoothAdapter.bondedDevices)
                if (device.type != BluetoothDevice.DEVICE_TYPE_LE) bondedBluetoothDevices.add(device)
            bondedBluetoothDevices.sortWith{ a: BluetoothDevice?, b: BluetoothDevice? -> BluetoothUtil.compareTo(a!!, b!!) }
            bondedBluetoothDevice = bondedBluetoothDevices.find { device -> device?.name == deviceName }

            // If we haven't connected before, try to discover it.
            if (bondedBluetoothDevice == null) {
                // Start scan if it hasn't been started yet.
                if (bluetoothAdapter.isDiscovering)
                    bluetoothAdapter.cancelDiscovery()
                bluetoothAdapter.startDiscovery()
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND, ACTION_BOND_STATE_CHANGED, ACTION_DISCOVERY_STARTED and ACTION_DISCOVERY_FINISHED.
    private val receiver = object : BroadcastReceiver() {
        private var foundDevice = false

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothDevice?.run { return }
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice = intent.parcelable(BluetoothDevice.EXTRA_DEVICE)
                    if (device.name == deviceName) {
                        device.createBond()
                        foundDevice = true
                        if (bluetoothAdapter.isDiscovering)
                            bluetoothAdapter.cancelDiscovery()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice = intent.parcelable(BluetoothDevice.EXTRA_DEVICE)
                    if (device.name == deviceName)
                        bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED ->
                    Toast.makeText(context, R.string.toast_discovery_started, Toast.LENGTH_SHORT).show()
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    if (!foundDevice)
                        bluetoothAdapter.startDiscovery()
            }
        }
    }
}