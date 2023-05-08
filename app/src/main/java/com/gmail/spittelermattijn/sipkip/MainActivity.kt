package com.gmail.spittelermattijn.sipkip

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import androidx.core.os.bundleOf
import com.gmail.spittelermattijn.sipkip.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var filePickerActivityResultLauncher: ActivityResultLauncher<String>
    private var getContentFd: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        filePickerActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                getContentFd = contentResolver.openFileDescriptor(uri, "r")
                val transcoder = OpusTranscoder(getContentFd!!)
                transcoder.onFinishedListener = ::onTranscoderFinished
                coroutineScope.launch { transcoder.start(openFileOutput(UUID.randomUUID().toString(), Context.MODE_PRIVATE)) }
            } else {
                binding.appBarMain.fab?.let {
                    Snackbar.make(it, getString(R.string.snackbar_no_file_picked), Snackbar.LENGTH_LONG).show()
                }
            }
        }

        binding.appBarMain.fab?.setOnClickListener { view -> filePickerActivityResultLauncher.launch("audio/*") }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        navHostFragment.view?.visibility = View.GONE
        val navController = navHostFragment.navController
        // Do this programmatically, since we want to pass arguments.
        navController.setGraph(
            R.navigation.mobile_navigation_main,
            bundleOf("bluetoothDevice" to intent.parcelable("android.bluetooth.BluetoothDevice") as BluetoothDevice)
        )

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

    fun onSerialError() {
        startActivity(Intent(this, FindDeviceActivity::class.java))
        finish()
    }

    private fun onTranscoderFinished(output: OutputStream) {
        binding.appBarMain.fab?.let {
            Snackbar.make(it, "Done!", Snackbar.LENGTH_LONG).show()
        }
        getContentFd?.close()
        getContentFd = null
        output.close()
    }
}