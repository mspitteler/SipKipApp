package com.gmail.spittelermattijn.sipkip

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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
import com.gmail.spittelermattijn.sipkip.databinding.ActivityMainBinding
import com.gmail.spittelermattijn.sipkip.ui.FragmentBase
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import jermit.protocol.SerialFileTransferSession
import jermit.protocol.xmodem.XmodemSender
import jermit.protocol.xmodem.XmodemSession
import java.io.OutputStream
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity(), ServiceConnection, SerialListener {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var filePickerActivityResultLauncher: ActivityResultLauncher<String>
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var bluetoothDevice: BluetoothDevice
    private var getContentFd: ParcelFileDescriptor? = null
    private var opusFileName: String? = null
    private var opusPacketsFileName: String? = null

    private var connected = Connected.False
    private var initialStart = true
    private var isResumed by Delegates.notNull<Boolean>()
    private var service: SerialService? = null

    private enum class Connected { False, Pending, True }

    override fun onCreate(savedInstanceState: Bundle?) {
        isResumed = false
        super.onCreate(savedInstanceState)

        bluetoothDevice = intent.parcelable("android.bluetooth.BluetoothDevice")
        bindService(Intent(this, SerialService::class.java), this, Context.BIND_AUTO_CREATE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        filePickerActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                getContentFd = contentResolver.openFileDescriptor(uri, "r")
                val getContentFileName = contentResolver.queryName(uri)
                opusFileName = "$getContentFileName.opus"
                opusPacketsFileName = "$getContentFileName.opus_packets"
                val opusOutput = openFileOutput(opusFileName, Context.MODE_PRIVATE)
                val opusPacketsOutput = openFileOutput(opusPacketsFileName, Context.MODE_PRIVATE)
                val transcoder = OpusTranscoder(getContentFd!!)
                transcoder.onFinishedListener = ::onTranscoderFinished
                transcoder.start(opusOutput, opusPacketsOutput)
            } else {
                binding.appBarMain.fab?.let {
                    Snackbar.make(it, getString(R.string.snackbar_no_file_picked), Snackbar.LENGTH_LONG).show()
                }
            }
        }

        binding.appBarMain.fab?.setOnClickListener { view -> filePickerActivityResultLauncher.launch("audio/*") }

        navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        navHostFragment.view?.visibility = View.GONE
        val navController = navHostFragment.navController
        // Do this programmatically, since we want to pass arguments.
        navController.setGraph(R.navigation.mobile_navigation_main)
        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            arguments?.putParcelable("bluetoothDevice", bluetoothDevice)
        }

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

    override fun onStart() {
        super.onStart()
        if (service != null)
            service!!.attach(this)
        // prevents service destroy on unbind from recreated activity caused by orientation change
        else
            startService(Intent(this, SerialService::class.java))

    }

    override fun onResume() {
        isResumed = true
        super.onResume()
        service?.run {
            if (initialStart) {
                initialStart = false
                runOnUiThread(this@MainActivity::connect)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    override fun onStop() {
        service?.run { if (!isChangingConfigurations) detach() }
        super.onStop()
    }

    override fun onDestroy() {
        if (connected !== Connected.False)
            disconnect()
        stopService(Intent(this, SerialService::class.java))
        try { unbindService(this) } catch (ignored: Exception) {}
        super.onDestroy()
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialService.SerialBinder).service
        service!!.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            runOnUiThread(this::connect)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    /*
     * Serial + UI
     */
    private fun connect() {
        Toast.makeText(this, R.string.toast_trying_to_connect, Toast.LENGTH_SHORT).show()
        connected = Connected.Pending
        try {
            val socket = SerialSocket(applicationContext, bluetoothDevice)
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        (navHostFragment.childFragmentManager.fragments[0] as FragmentBase).
            viewModel.serialWriteCallback = null
        connected = Connected.False
        service!!.disconnect()
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        Toast.makeText(this, R.string.toast_connected, Toast.LENGTH_SHORT).show()
        connected = Connected.True
        (navHostFragment.childFragmentManager.fragments[0] as FragmentBase).
            viewModel.serialWriteCallback = service!!::write
    }

    override fun onSerialConnectError(e: Exception?) {
        Toast.makeText(this, getString(R.string.toast_connection_failed, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        onSerialError()
    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray?>()
        datas.add(data)
        (navHostFragment.childFragmentManager.fragments[0] as FragmentBase)
            .viewModel.onSerialRead(datas)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        (navHostFragment.childFragmentManager.fragments[0] as FragmentBase).
            viewModel.onSerialRead(datas)
    }

    override fun onSerialIoError(e: Exception?) {
        Toast.makeText(this, getString(R.string.toast_connection_lost, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        onSerialError()
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

    private fun onSerialError() {
        startActivity(Intent(this, FindDeviceActivity::class.java))
        finish()
    }

    private fun onTranscoderFinished(opusOutput: OutputStream, opusPacketsOutput: OutputStream) {
        binding.appBarMain.fab?.let {
            Snackbar.make(it, "Done!", Snackbar.LENGTH_LONG).show()
        }
        getContentFd?.close()
        getContentFd = null
        opusOutput.close()
        opusPacketsOutput.close()

        // Allow CRC. This will automatically fallback to vanilla if the receiver initiates with NAK instead of 'C'.
        // We can't default to 1K because we can't guarantee that the receiver will know how to handle it.

        // Permit 1K.  This will fallback to vanilla if they use NAK.
        val flavor = if (DEFAULT_XMODEM_BLOCK_SIZE >= 1024) XmodemSession.Flavor.X_1K else XmodemSession.Flavor.CRC
        // Open only the first file and send it.
        // Open only the first file and send it.
        val sx = XmodemSender(flavor, null, null, opusFileName)
        opusFileName = null

        val session: SerialFileTransferSession = sx.session
        val transferThread = Thread(sx)

        opusPacketsFileName = null
    }

    private fun ContentResolver.queryName(uri: Uri): String {
        val returnCursor = query(uri, null, null, null, null)!!
        val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }

    private companion object {
        const val DEFAULT_XMODEM_BLOCK_SIZE = 1024
    }
}