package com.gmail.spittelermattijn.sipkip.ui.music

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.databinding.FragmentMusicBinding
import com.gmail.spittelermattijn.sipkip.databinding.ItemMusicBinding
import com.gmail.spittelermattijn.sipkip.ui.FragmentBase


/**
 * Fragment that demonstrates a responsive layout pattern where the format of the content
 * transforms depending on the size of the screen. Specifically this Fragment shows items in
 * the [RecyclerView] using LinearLayoutManager in a small screen
 * and shows items using GridLayoutManager in a large screen.
 */
class MusicFragment : FragmentBase() {
    private var _binding: FragmentMusicBinding? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var onCreateViewCalled = false

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val musicViewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        val root = binding.root

        val recyclerView = binding.recyclerviewTransform
        val loadingView = binding.loadingPanel
        val adapter = TransformAdapter()
        recyclerView.adapter = adapter
        bluetoothDevice?.let {
            recyclerView.visibility = View.VISIBLE
            loadingView.visibility = View.GONE
        }
        musicViewModel.texts.observe(viewLifecycleOwner) { adapter.submitList(it) }

        onCreateViewCalled = true
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        onCreateViewCalled = false
    }

    override fun onBluetoothDeviceFound(device: BluetoothDevice) {
        bluetoothDevice = device

        if (onCreateViewCalled) {
            val recyclerView = binding.recyclerviewTransform
            val loadingView = binding.loadingPanel
            recyclerView.visibility = View.VISIBLE
            loadingView.visibility = View.GONE
        }
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
}