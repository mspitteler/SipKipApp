package com.gmail.spittelermattijn.sipkip

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
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
import com.gmail.spittelermattijn.sipkip.serial.SerialListener
import com.gmail.spittelermattijn.sipkip.serial.SerialService
import com.gmail.spittelermattijn.sipkip.serial.SerialSocket
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface
import com.gmail.spittelermattijn.sipkip.util.getUriWithType
import com.gmail.spittelermattijn.sipkip.util.ACTION_MUSIC_PLAYER
import com.gmail.spittelermattijn.sipkip.util.coroutineScope
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths
import com.gmail.spittelermattijn.sipkip.util.parcelable
import com.gmail.spittelermattijn.sipkip.util.queryName
import com.gmail.spittelermattijn.sipkip.util.showFirstDirectoryPicker
import com.gmail.spittelermattijn.sipkip.util.showNewOrPreviouslyUploadedPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import jermit.protocol.SerialFileTransferSession
import jermit.protocol.xmodem.XmodemSender
import jermit.protocol.xmodem.XmodemSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.properties.Delegates
import kotlin.reflect.KFunction1
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class MainActivity : AppCompatActivity(), ServiceConnection, SerialListener {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var filePickerActivityResultLauncher: ActivityResultLauncher<String>
    private lateinit var previouslyUploadedActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var bluetoothDevice: BluetoothDevice
    private val currentFragment: FragmentInterface
        get() = (navHostFragment.childFragmentManager.fragments[0] as FragmentInterface)
    private var getContentFd: ParcelFileDescriptor? = null
    private var opusFileName: String? = null
    private var opusPacketsFileName: String? = null

    private var connected = Connected.False
    private var initialStart = true
    private var isResumed by Delegates.notNull<Boolean>()
    private var service: SerialService? = null
    private val serialQueue: BlockingQueue<Byte> = ArrayBlockingQueue(10000)
    private var serialIsBlocking by Delegates.observable(false) { _, _, new ->
        currentFragment.viewModel.serialWriteCallback = if (new) null else service!!::write
    }

    private enum class Connected { False, Pending, True }

    override fun onCreate(savedInstanceState: Bundle?) {
        isResumed = false
        Preferences.addContextGetter { this }
        super.onCreate(savedInstanceState)

        val shared = intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_VIEW, Intent().ACTION_MUSIC_PLAYER)

        if (!intent.hasExtra("android.bluetooth.BluetoothDevice") && shared) {
            Toast.makeText(this, R.string.toast_share_first_try_connect, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bluetoothDevice = intent.parcelable("android.bluetooth.BluetoothDevice")

        bindService(Intent(this, SerialService::class.java), this, Context.BIND_AUTO_CREATE)
        inflateLayout()

        filePickerActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                startTranscoderFromUri(uri)
            } else {
                binding.appBarMain.fab?.let {
                    Snackbar.make(it, R.string.snackbar_no_file_picked, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        previouslyUploadedActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val itemPath = result.data?.dataString
            if (itemPath != null) {
                println("Picked filename: $itemPath")
                opusFileName = "$itemPath.opus"
                opusPacketsFileName = "$itemPath.opus_packets"
                MaterialAlertDialogBuilder(this).showFirstDirectoryPicker { startSerialUpload(it) }
            } else {
                binding.appBarMain.fab?.let {
                    Snackbar.make(it, R.string.snackbar_no_file_picked, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        setupNavigation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val manager = supportFragmentManager
        manager.beginTransaction().detach(navHostFragment).commit()
        inflateLayout()
        manager.beginTransaction().attach(navHostFragment).commit()
        setupNavigation()
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
        val uri = intent?.getUriWithType("audio/")
        println("uri: $uri")
        if (uri != null) {
            startTranscoderFromUri(uri)
        } else {
            Toast.makeText(this, R.string.toast_no_valid_uri_found_in_intent, Toast.LENGTH_SHORT).show()
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
        currentFragment.viewModel.serialWriteCallback = null
        connected = Connected.False
        service!!.disconnect()
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        Toast.makeText(this, R.string.toast_connected, Toast.LENGTH_SHORT).show()
        connected = Connected.True
        currentFragment.viewModel.serialWriteCallback = service!!::write
    }

    override fun onSerialConnectError(e: Exception?) {
        Toast.makeText(this, getString(R.string.toast_connection_failed, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        onSerialError()
    }

    override fun onSerialRead(data: ByteArray?) {
        if (serialIsBlocking) {
            data?.forEach { serialQueue.add(it) }
        } else {
            val datas = ArrayDeque<ByteArray?>()
            datas.add(data)
            currentFragment.viewModel.onSerialRead(datas)
        }
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        if (serialIsBlocking)
            datas?.forEach { it?.forEach { byte -> serialQueue.add(byte) } }
        else
            currentFragment.viewModel.onSerialRead(datas)
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

    private fun onSerialError() {
        startActivity(Intent(this, FindDeviceActivity::class.java))
        finish()
    }

    private fun inflateLayout() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        window.statusBarColor = SurfaceColors.SURFACE_0.getColor(this)
        val materialShapeDrawable = binding.appBarMain.toolbar.background as MaterialShapeDrawable
        materialShapeDrawable.shapeAppearanceModel = materialShapeDrawable.shapeAppearanceModel.toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, Int.MAX_VALUE.toFloat()).build()
    }

    private fun setupNavigation() {
        binding.appBarMain.fab?.setOnClickListener { _ ->
            val newCb = { filePickerActivityResultLauncher.launch("audio/*") }
            if (filesDir.listFiles()?.map { it.name }?.filterValidOpusPaths().isNullOrEmpty()) {
                newCb()
            } else {
                MaterialAlertDialogBuilder(this).showNewOrPreviouslyUploadedPicker(newCb) {
                    previouslyUploadedActivityResultLauncher.launch(Intent(this, PreviouslyUploadedActivity::class.java))
                }
            }
        }

        navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        navHostFragment.view?.visibility = View.GONE
        val navController = navHostFragment.navController

        // Do this programmatically, since we want to pass arguments.
        navController.addOnDestinationChangedListener { _, _, arguments ->
            arguments?.putParcelable("bluetoothDevice", bluetoothDevice)
        }

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_music, R.id.nav_play, R.id.nav_learn, R.id.nav_preferences, R.id.nav_bt_settings
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

    private fun startTranscoderFromUri(uri: Uri) {
        getContentFd = contentResolver.openFileDescriptor(uri, "r")
        val getContentFileName = contentResolver.queryName(uri).replace("""\s""".toRegex(), "_")
        println("Picked filename: $getContentFileName")
        opusFileName = "$getContentFileName.opus"
        opusPacketsFileName = "$getContentFileName.opus_packets"
        val opusOutput = openFileOutput(opusFileName, Context.MODE_PRIVATE)
        val opusPacketsOutput = openFileOutput(opusPacketsFileName, Context.MODE_PRIVATE)
        val transcoder = OpusTranscoder(getContentFd!!)
        transcoder.onStartedListener = ::onTranscoderStarted
        transcoder.onFinishedListener = ::onTranscoderFinished
        transcoder.start(opusOutput, opusPacketsOutput)
    }

    private fun onTranscoderStarted(): Any? {
        var bar: Snackbar? = null
        binding.appBarMain.fab?.let {
            bar = Snackbar.make(it, R.string.snackbar_processing_file, Snackbar.LENGTH_INDEFINITE)
            val snackView = bar!!.view as SnackbarLayout
            val progressBar = LinearProgressIndicator(this)
            progressBar.isIndeterminate = true
            snackView.addView(progressBar)
            bar!!.show()
        }
        return bar
    }

    private fun onTranscoderFinished(opusOutput: OutputStream, opusPacketsOutput: OutputStream, args: Any?) {
        getContentFd?.close()
        getContentFd = null
        opusOutput.close()
        opusPacketsOutput.close()
        (args as? Snackbar?)?.dismiss()

        MaterialAlertDialogBuilder(this).showFirstDirectoryPicker { startSerialUpload(it) }
    }

    // Returns true if a new upload was started.
    private fun startSerialUpload(firstDirectory: String): Boolean {
        val `1kThreshold`: Int = Preferences[R.string.xmodem_1k_threshold_key]
        val useCrc: Boolean = Preferences[R.string.xmodem_use_crc_key]

        val fileName = (if (opusFileName != null) {
            val name = opusFileName; opusFileName = null; name
        } else {
            val name = opusPacketsFileName; opusPacketsFileName = null; name
        }) ?: return false
        val filePath = "$filesDir/$fileName"

        // Allow CRC. This will automatically fallback to vanilla if the receiver initiates with NAK instead of 'C'.
        // We can't default to 1K because we can't guarantee that the receiver will know how to handle it.

        // Permit 1K.  This will fallback to vanilla if they use NAK.
        val flavor = if (File(filePath).length() >= `1kThreshold`)
            XmodemSession.Flavor.X_1K
        else if (useCrc)
            XmodemSession.Flavor.CRC
        else
            XmodemSession.Flavor.VANILLA
        // Open only the first file and send it.
        val sx = XmodemSender(flavor,
            object: InputStream() {
                override fun read() = serialQueue.take().toInt()
                override fun available() = serialQueue.size
            },
            object : OutputStream() {
                override fun write(b: ByteArray?) = service!!.write(b)
                override fun write(b: Int) = write(ByteArray(1) { b.toByte() })
                override fun write(b: ByteArray?, off: Int, len: Int) = write(b?.copyOfRange(off, off + len))
            }, filePath
        )

        setupSerialUpload(sx.session, firstDirectory, fileName, ::startSerialUpload)
        val transferThread = Thread(sx)
        transferThread.start()
        return true
    }

    private fun setupSerialUpload(session: SerialFileTransferSession, firstDirectory: String, fileName: String, cb: KFunction1<String, Boolean>) {
        val timeout: Int = Preferences[R.string.bluetooth_command_timeout_key]

        var snackBar: Snackbar? = null
        var progressBar: ProgressBar? = null
        binding.appBarMain.fab?.post {
            snackBar = Snackbar.make(binding.appBarMain.fab!!, R.string.snackbar_upload_progress, Snackbar.LENGTH_LONG)
            val snackView = snackBar!!.view as SnackbarLayout
            progressBar = LinearProgressIndicator(this)
            progressBar!!.isIndeterminate = false
            progressBar!!.max = 100 // percent
            snackView.addView(progressBar)
            snackBar!!.show()
        }

        coroutineScope.launch {
            serialIsBlocking = true
            delay(timeout.toDuration(DurationUnit.MILLISECONDS))
            service!!.write("rm /littlefs/${currentFragment.viewModel.littleFsPath}/$firstDirectory/$fileName\n".toByteArray())
            delay(timeout.toDuration(DurationUnit.MILLISECONDS))
            service!!.write("rx /littlefs/${currentFragment.viewModel.littleFsPath}/$firstDirectory/$fileName\n".toByteArray())
            serialQueue.clear()

            // Emit messages as they are recorded.
            var messageCount = 0
            var done: Boolean
            while (true) {
                synchronized(session) {
                    done = when (session.state) {
                        SerialFileTransferSession.State.ABORT -> {
                            // Reset serial to normal mode.
                            serialIsBlocking = false
                            true
                        }
                        SerialFileTransferSession.State.END -> {
                            // Recursive call to upload the second file, also restore serial to normal mode if no new upload is started.
                            serialIsBlocking = cb(firstDirectory)
                            // All done, bail out.
                            true
                        }
                        else -> false
                    }
                }
                if (done)
                    break
                try {
                    // Wait for a notification on the session.
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    synchronized(session) { (session as Object).wait(100) }
                } catch (e: InterruptedException) {
                    // SQUASH
                }
                synchronized(session) {
                    if (session.messageCount() > messageCount) {
                        for (i in messageCount until session.messageCount()) {
                            println(session.getMessage(i).message)
                            messageCount++
                        }
                    }
                    val percent = session.percentComplete.toInt()
                    //session.cancelTransfer(false)
                    // Restart timeout.
                    snackBar?.show()
                    progressBar?.post { progressBar!!.progress = percent }
                }
            }
            runOnUiThread { snackBar?.setText(session.lastMessage.message) }
        }
    }
}
