package com.gmail.spittelermattijn.sipkip.ui.music

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
import androidx.navigation.fragment.navArgs
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
    private lateinit var musicViewModel: MusicViewModel

    private var _binding: FragmentMusicBinding? = null
    private val args by navArgs<MusicFragmentArgs>()
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        val root = binding.root

        val recyclerView = binding.recyclerviewMusic
        val adapter = MusicAdapter()
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
            val socket = SerialSocket(requireActivity().applicationContext, args.bluetoothDevice)
            service!!.connect(socket)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        musicViewModel.writeCallback = null
        connected = Connected.False
        service!!.disconnect()
    }

    class MusicAdapter :
        ListAdapter<MusicViewModel.Item, MusicViewHolder>(object : DiffUtil.ItemCallback<MusicViewModel.Item>() {

            override fun areItemsTheSame(oldItem: MusicViewModel.Item, newItem: MusicViewModel.Item): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: MusicViewModel.Item, newItem: MusicViewModel.Item): Boolean =
                oldItem == newItem
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
            val binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.context))
            return MusicViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
            holder.textView.text = getItem(position).path
            holder.imageView.setImageDrawable(
                ResourcesCompat.getDrawable(holder.imageView.resources, getItem(position).drawable, null)
            )
        }
    }

    class MusicViewHolder(binding: ItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val imageView: ImageView = binding.imageViewItemMusic
        val textView: TextView = binding.textViewItemMusic
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        Toast.makeText(context, R.string.toast_connected, Toast.LENGTH_SHORT).show()
        connected = Connected.True
        musicViewModel.writeCallback = service!!::write
    }

    override fun onSerialConnectError(e: Exception?) {
        Toast.makeText(context, getString(R.string.toast_connection_failed, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        (activity as? MainActivity)?.onSerialError()
    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray?>()
        datas.add(data)
        musicViewModel.onSerialRead(datas)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray?>?) {
        musicViewModel.onSerialRead(datas)
    }

    override fun onSerialIoError(e: Exception?) {
        Toast.makeText(context, getString(R.string.toast_connection_lost, e?.message), Toast.LENGTH_SHORT).show()
        disconnect()
        (activity as? MainActivity)?.onSerialError()
    }
}