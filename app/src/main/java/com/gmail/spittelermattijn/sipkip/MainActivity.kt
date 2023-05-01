package com.gmail.spittelermattijn.sipkip

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import com.gmail.spittelermattijn.sipkip.databinding.ActivityMainBinding
import com.gmail.spittelermattijn.sipkip.ui.FragmentBase

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val bondedBluetoothDevices: ArrayList<BluetoothDevice?> = ArrayList()
    private lateinit var requestBluetoothPermissionLauncherForRefresh: ActivityResultLauncher<Array<String>>
    private var hasPermissions = false
    private var bluetoothDevice: BluetoothDevice? = null
        set(device) {
            field = device
            field?.let {
                val navHostFragment =
                    (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
                navHostFragment.childFragmentManager.fragments.forEach { fragment -> (fragment as? FragmentBase?)?.onBluetoothDeviceFound(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        requestBluetoothPermissionLauncherForRefresh = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) {
                granted: Map<String, Boolean>? ->
            BluetoothUtil.onPermissionsResult(this, granted!!, this::refresh)
        }

        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.also { it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
            .also { it.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)

        registerReceiver(receiver, filter)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        refresh()

        binding.appBarMain.fab?.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        navHostFragment.view?.visibility = View.GONE
        val navController = navHostFragment.navController

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_music, R.id.nav_play, R.id.nav_learn, R.id.nav_settings, R.id.nav_bt_settings
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_music, R.id.nav_play, R.id.nav_learn
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            bluetoothAdapter.isDiscovering)
            bluetoothAdapter.cancelDiscovery()
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
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        bondedBluetoothDevices.clear()
        hasPermissions = BluetoothUtil.hasPermissions(this, requestBluetoothPermissionLauncherForRefresh)
        if (hasPermissions) {
            for (device in bluetoothAdapter.bondedDevices)
                if (device.type != BluetoothDevice.DEVICE_TYPE_LE) bondedBluetoothDevices.add(device)
            bondedBluetoothDevices.sortWith{ a: BluetoothDevice?, b: BluetoothDevice? -> BluetoothUtil.compareTo(a!!, b!!) }

            // Start scan if it hasn't been started yet.
            if (bluetoothAdapter.isDiscovering)
                bluetoothAdapter.cancelDiscovery()
            bluetoothAdapter.startDiscovery()
        }
        bluetoothDevice = bondedBluetoothDevices.find { device -> device?.name == Constants.BLUETOOTH_DEVICE_NAME }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        private var foundDevice = false

        inline fun <reified T : Parcelable> Intent.parcelable(key: String): T = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)!!
            else -> @Suppress("Deprecation") (getParcelableExtra(key) as T?)!!
        }

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothDevice?.run { return }
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice = intent.parcelable(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device.name
                    if (deviceName == Constants.BLUETOOTH_DEVICE_NAME) {
                        device.createBond()
                        foundDevice = true
                        if (bluetoothAdapter.isDiscovering)
                            bluetoothAdapter.cancelDiscovery()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice = intent.parcelable(BluetoothDevice.EXTRA_DEVICE)
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