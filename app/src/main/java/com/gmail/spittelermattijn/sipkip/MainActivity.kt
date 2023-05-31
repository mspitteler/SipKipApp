package com.gmail.spittelermattijn.sipkip

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.gmail.spittelermattijn.sipkip.databinding.ActivityMainBinding
import com.gmail.spittelermattijn.sipkip.opus.OpusTranscoderListener
import com.gmail.spittelermattijn.sipkip.serial.SerialListener
import com.gmail.spittelermattijn.sipkip.serial.SerialService
import com.gmail.spittelermattijn.sipkip.serial.SerialSocket
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface
import com.gmail.spittelermattijn.sipkip.util.getUriWithType
import com.gmail.spittelermattijn.sipkip.util.actionIsShared
import com.gmail.spittelermattijn.sipkip.util.parcelable
import com.gmail.spittelermattijn.sipkip.util.showFirstDirectoryPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity(), ServiceConnection, SerialListener, OpusTranscoderListener {
    private lateinit var appBarConfiguration: AppBarConfiguration
    internal lateinit var binding: ActivityMainBinding
        private set
    private lateinit var filePickerActivityResultLauncher: ActivityResultLauncher<String>
    private lateinit var previouslyUploadedActivityResultLauncher: ActivityResultLauncher<Intent>
    private var navHostFragment: NavHostFragment? = null
    private lateinit var bluetoothDevice: BluetoothDevice
    private val currentFragment: FragmentInterface?
        get() = (navHostFragment?.childFragmentManager?.fragments?.get(0) as FragmentInterface?)
    private var getContentFd: ParcelFileDescriptor? = null
    internal var opusFileName: String? = null
    internal var opusPacketsFileName: String? = null

    private var connected = Connected.False
    private var initialStart = true
    private var isResumed by Delegates.notNull<Boolean>()
    private var service: SerialService? = null
    private val serialQueue: BlockingQueue<Byte> = ArrayBlockingQueue(10000)
    /*
     * TODO: Make sure that the app doesn't crash if a fragment is accessed if it is not yet or not anymore attached.
     *       This can happen if a transfer is aborted after we switched fragments for example.
     */
    internal var serialIsBlocking by Delegates.observable(false) { _, _, new ->
        currentFragment!!.viewModel.serialWriteCallback = if (new) null else service!!::write
    }

    private enum class Connected { False, Pending, True }

    override fun onCreate(savedInstanceState: Bundle?) {
        isResumed = false
        Preferences.addContextGetter { this }
        super.onCreate(savedInstanceState)

        if (!intent.hasExtra("android.bluetooth.BluetoothDevice") && intent.actionIsShared) {
            Toast.makeText(this, R.string.toast_share_first_try_connect, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bluetoothDevice = intent.parcelable("android.bluetooth.BluetoothDevice")

        bindService(Intent(this, SerialService::class.java), this, Context.BIND_AUTO_CREATE)
        binding = inflateLayout()

        filePickerActivityResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                getContentFd = startTranscoderFromUri(uri)
            else
                binding.appBarMain.fab?.let { Snackbar.make(it, R.string.snackbar_no_file_picked, Snackbar.LENGTH_LONG).show() }
        }

        previouslyUploadedActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val itemPath = result.data?.dataString
            if (itemPath != null) {
                println("Picked filename: $itemPath")
                opusFileName = "$itemPath.opus"
                opusPacketsFileName = "$itemPath.opus_packets"
                MaterialAlertDialogBuilder(this).showFirstDirectoryPicker {
                    startSerialUpload("${currentFragment!!.viewModel.littleFsPath}/$it", serialQueue) { service!!::write }
                }
            } else {
                binding.appBarMain.fab?.let { Snackbar.make(it, R.string.snackbar_no_file_picked, Snackbar.LENGTH_LONG).show() }
            }
        }

        val (n, a) = setupNavigation(filePickerActivityResultLauncher, previouslyUploadedActivityResultLauncher) { _, _, arguments ->
            arguments?.putParcelable("bluetoothDevice", bluetoothDevice)
        }
        navHostFragment = n
        appBarConfiguration = a
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val manager = supportFragmentManager
        navHostFragment?.let {
            navHostFragment = null
            manager.beginTransaction().detach(it).commit()
        }
        binding = inflateLayout()
        val (n, a) = setupNavigation(filePickerActivityResultLauncher, previouslyUploadedActivityResultLauncher) { _, _, arguments ->
            arguments?.putParcelable("bluetoothDevice", bluetoothDevice)
        }
        manager.beginTransaction().attach(n).commit()
        navHostFragment = n
        appBarConfiguration = a
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent == null || !intent.actionIsShared)
            return
        val uri = intent.getUriWithType("audio/")
        println("uri: $uri")
        if (uri != null)
            getContentFd = startTranscoderFromUri(uri)
        else
            Toast.makeText(this, R.string.toast_no_valid_uri_found_in_intent, Toast.LENGTH_SHORT).show()
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
        Preferences.removeContextGetter(this)
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
        currentFragment!!.viewModel.serialWriteCallback = null
        connected = Connected.False
        service!!.disconnect()
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        Toast.makeText(this, R.string.toast_connected, Toast.LENGTH_SHORT).show()
        connected = Connected.True
        currentFragment!!.viewModel.serialWriteCallback = service!!::write
    }

    override fun onSerialConnectError(e: Exception?) {
        Toast.makeText(this, getString(R.string.toast_connection_failed, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        startActivity(Intent(this, FindDeviceActivity::class.java))
        finish()
    }

    override fun onSerialRead(data: ByteArray?) {
        if (serialIsBlocking) {
            data?.forEach { serialQueue.add(it) }
        } else {
            val datas = ArrayDeque<ByteArray?>()
            datas.add(data)
            currentFragment!!.viewModel.onSerialRead(datas)
        }
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        if (serialIsBlocking)
            datas?.forEach { it?.forEach { byte -> serialQueue.add(byte) } }
        else
            currentFragment!!.viewModel.onSerialRead(datas)
    }

    override fun onSerialIoError(e: Exception?) {
        Toast.makeText(this, getString(R.string.toast_connection_lost, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        startActivity(Intent(this, FindDeviceActivity::class.java))
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_bt_settings -> {
                val intent = Intent()
                intent.action = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                startActivity(intent)
            }
            R.id.nav_preferences -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_preferences)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onTranscoderStarted(): Any? {
        var bar: Snackbar? = null
        binding.appBarMain.fab?.let {
            bar = Snackbar.make(it, R.string.snackbar_processing_file, Snackbar.LENGTH_INDEFINITE)
            val snackView = bar!!.view as Snackbar.SnackbarLayout
            val progressBar = LinearProgressIndicator(this)
            progressBar.isIndeterminate = true
            snackView.addView(progressBar)
            bar!!.show()
        }
        return bar
    }

    override fun onTranscoderFinished(opusOutput: OutputStream, opusPacketsOutput: OutputStream, args: Any?) {
        getContentFd?.close()
        getContentFd = null
        opusOutput.close()
        opusPacketsOutput.close()
        (args as? Snackbar?)?.dismiss()

        MaterialAlertDialogBuilder(this).showFirstDirectoryPicker {
            startSerialUpload("${currentFragment!!.viewModel.littleFsPath}/$it", serialQueue) { service!!::write }
        }
    }
}
