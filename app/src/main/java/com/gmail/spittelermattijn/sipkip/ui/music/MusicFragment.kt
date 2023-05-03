package com.gmail.spittelermattijn.sipkip.ui.music

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gmail.spittelermattijn.sipkip.*
import com.gmail.spittelermattijn.sipkip.SerialService.SerialBinder
import com.gmail.spittelermattijn.sipkip.databinding.FragmentMusicBinding
import com.gmail.spittelermattijn.sipkip.databinding.ItemMusicBinding


/**
 * Fragment that demonstrates a responsive layout pattern where the format of the content
 * transforms depending on the size of the screen. Specifically this Fragment shows items in
 * the [RecyclerView] using LinearLayoutManager in a small screen
 * and shows items using GridLayoutManager in a large screen.
 */
class MusicFragment : Fragment(), ServiceConnection, SerialListener {
    private var _binding: FragmentMusicBinding? = null
    private lateinit var bluetoothDevice: BluetoothDevice
    private var connected = Connected.False
    private var initialStart = true
    private var service: SerialService? = null

    private enum class Connected { False, Pending, True }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().bindService(Intent(activity, SerialService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothDevice = requireActivity().intent.parcelable("android.bluetooth.BluetoothDevice")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        val root = binding.root

        val recyclerView = binding.recyclerviewTransform
        val adapter = TransformAdapter()
        recyclerView.adapter = adapter
        musicViewModel.texts.observe(viewLifecycleOwner) { adapter.submitList(it) }

        return root
    }

    override fun onStart() {
        super.onStart()
        if (service != null)
            service!!.attach(this)
        // prevents service destroy on unbind from recreated activity caused by orientation change
        else
            requireActivity().startService(Intent(activity, SerialService::class.java))

    }

    override fun onResume() {
        super.onResume()
        service?.run {
            if (initialStart) {
                initialStart = false
                requireActivity().runOnUiThread(this@MusicFragment::connect)
            }
        }
    }

    override fun onStop() {
        service?.run { if (!requireActivity().isChangingConfigurations) detach() }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        if (connected !== Connected.False)
            disconnect()
        requireActivity().stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
    }

    override fun onDetach() {
        try { requireActivity().unbindService(this) } catch (ignored: Exception) {}
        super.onDetach()
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialBinder).service
        service!!.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread(this::connect)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            Toast.makeText(context, R.string.toast_trying_to_connect, Toast.LENGTH_SHORT).show()
            connected = Connected.Pending
            val socket = SerialSocket(requireActivity().applicationContext, bluetoothDevice)
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
    }

    class TransformAdapter :
        ListAdapter<String, TransformViewHolder>(object : DiffUtil.ItemCallback<String>() {

            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean =
                oldItem == newItem
        }) {

        private val drawables = listOf(
            R.drawable.ic_blue_square_button,
            R.drawable.ic_beak_switch,
            R.drawable.ic_purple_star_button,
            R.drawable.ic_yellow_heart_button,
            R.drawable.ic_red_triangle_button,
            R.drawable.ic_blue_square_button,
            R.drawable.ic_beak_switch,
            R.drawable.ic_purple_star_button,
            R.drawable.ic_yellow_heart_button,
            R.drawable.ic_red_triangle_button,
            R.drawable.ic_blue_square_button,
            R.drawable.ic_beak_switch,
            R.drawable.ic_purple_star_button,
            R.drawable.ic_yellow_heart_button,
            R.drawable.ic_red_triangle_button,
            R.drawable.ic_blue_square_button,
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransformViewHolder {
            val binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.context))
            return TransformViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TransformViewHolder, position: Int) {
            holder.textView.text = getItem(position)
            holder.imageView.setImageDrawable(
                ResourcesCompat.getDrawable(holder.imageView.resources, drawables[position], null)
            )
        }
    }

    class TransformViewHolder(binding: ItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val imageView: ImageView = binding.imageViewItemTransform
        val textView: TextView = binding.textViewItemTransform
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        Toast.makeText(context, R.string.toast_connected, Toast.LENGTH_SHORT).show()
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception?) {
        Toast.makeText(context, getString(R.string.toast_connection_failed, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
    }

    override fun onSerialRead(data: ByteArray?) {
        //TODO("Not yet implemented")
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        //TODO("Not yet implemented")
    }

    override fun onSerialIoError(e: Exception?) {
        Toast.makeText(context, getString(R.string.toast_connection_lost, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
    }
}