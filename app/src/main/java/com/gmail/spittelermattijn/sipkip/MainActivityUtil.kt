package com.gmail.spittelermattijn.sipkip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.gmail.spittelermattijn.sipkip.databinding.ActivityMainBinding
import com.gmail.spittelermattijn.sipkip.opus.OpusTranscoder
import com.gmail.spittelermattijn.sipkip.util.coroutineScope
import com.gmail.spittelermattijn.sipkip.util.filterValidOpusPaths
import com.gmail.spittelermattijn.sipkip.util.queryName
import com.gmail.spittelermattijn.sipkip.util.showNewOrPreviouslyUploadedPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import jermit.protocol.SerialFileTransferSession
import jermit.protocol.xmodem.XmodemSender
import jermit.protocol.xmodem.XmodemSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.BlockingQueue
import kotlin.reflect.KFunction3
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun MainActivity.inflateLayout() = ActivityMainBinding.inflate(layoutInflater).also {
    setContentView(it.root)
    setSupportActionBar(it.appBarMain.toolbar)
    val materialShapeDrawable = it.appBarMain.toolbar.background as MaterialShapeDrawable
    materialShapeDrawable.shapeAppearanceModel = materialShapeDrawable.shapeAppearanceModel.toBuilder()
        .setAllCorners(CornerFamily.ROUNDED, Int.MAX_VALUE.toFloat()).build()
}

fun MainActivity.setupNavigation(
    filePickerActivityResultLauncher: ActivityResultLauncher<String>, previouslyUploadedActivityResultLauncher: ActivityResultLauncher<Intent>,
    onDestinationChangedListener: NavController.OnDestinationChangedListener
): Pair<NavHostFragment, AppBarConfiguration> {
    var appBarConfiguration: AppBarConfiguration? = null

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

    val navHostFragment =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
    navHostFragment.view?.visibility = View.GONE
    val navController = navHostFragment.navController

    // Do this programmatically, since we want to pass arguments.
    navController.addOnDestinationChangedListener(onDestinationChangedListener)

    binding.navView?.let {
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_music, R.id.nav_play, R.id.nav_learn, R.id.nav_preferences, R.id.nav_bt_settings
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration!!)
        it.setupWithNavController(navController)
    }

    binding.appBarMain.contentMain.bottomNavView?.let {
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_music, R.id.nav_play, R.id.nav_learn
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration!!)
        it.setupWithNavController(navController)
    }
    return Pair(navHostFragment, appBarConfiguration!!)
}

fun MainActivity.startTranscoderFromUri(uri: Uri) = contentResolver.openFileDescriptor(uri, "r")?.also {
    val getContentFileName = contentResolver.queryName(uri).replace("""\s""".toRegex(), "_")
    println("Picked filename: $getContentFileName")
    opusFileName = "$getContentFileName.opus"
    opusPacketsFileName = "$getContentFileName.opus_packets"
    val opusOutput = openFileOutput(opusFileName, Context.MODE_PRIVATE)
    val opusPacketsOutput = openFileOutput(opusPacketsFileName, Context.MODE_PRIVATE)
    val transcoder = OpusTranscoder(this, it)
    transcoder.start(opusOutput, opusPacketsOutput)
}

// Returns true if a new upload was started.
fun MainActivity.startSerialUpload(pathPrefix: String, serialQueue: BlockingQueue<Byte>, writeCbGetter: () -> ((ByteArray?) -> Unit)): Boolean {
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
            override fun write(b: ByteArray?) = writeCbGetter()(b)
            override fun write(b: Int) = write(ByteArray(1) { b.toByte() })
            override fun write(b: ByteArray?, off: Int, len: Int) = write(b?.copyOfRange(off, off + len))
        }, filePath
    )

    setupSerialUpload(sx.session, pathPrefix, fileName, serialQueue, writeCbGetter, ::startSerialUpload)
    val transferThread = Thread(sx)
    transferThread.start()
    return true
}

private fun MainActivity.setupSerialUpload(
    session: SerialFileTransferSession, pathPrefix: String, fileName: String, serialQueue: BlockingQueue<Byte>,
    writeCbGetter: () -> ((ByteArray?) -> Unit), cb: KFunction3<String, BlockingQueue<Byte>, () -> ((ByteArray?) -> Unit), Boolean>
) {
    val timeout: Int = Preferences[R.string.bluetooth_command_timeout_key]

    var snackBar: Snackbar? = null
    var progressBar: ProgressBar? = null
    binding.appBarMain.fab?.post {
        snackBar = Snackbar.make(binding.appBarMain.fab!!, R.string.snackbar_upload_progress, Snackbar.LENGTH_LONG)
        val snackView = snackBar!!.view as Snackbar.SnackbarLayout
        progressBar = LinearProgressIndicator(this)
        progressBar!!.isIndeterminate = false
        progressBar!!.max = 100 // percent
        snackView.addView(progressBar)
        snackBar!!.show()
    }

    coroutineScope.launch {
        serialIsBlocking = true
        delay(timeout.toDuration(DurationUnit.MILLISECONDS))
        writeCbGetter()("rm /littlefs/$pathPrefix/$fileName\n".toByteArray())
        delay(timeout.toDuration(DurationUnit.MILLISECONDS))
        writeCbGetter()("rx /littlefs/$pathPrefix/$fileName\n".toByteArray())
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
                        serialIsBlocking = cb(pathPrefix, serialQueue, writeCbGetter)
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