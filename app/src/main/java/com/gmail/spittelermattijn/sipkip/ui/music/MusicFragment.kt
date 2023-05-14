package com.gmail.spittelermattijn.sipkip.ui.music

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
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
    override lateinit var viewModel: MusicViewModel
    private lateinit var bluetoothDevice: BluetoothDevice

    private var _binding: FragmentMusicBinding? = null
    override val args by navArgs<MusicFragmentArgs>()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bluetoothDevice = args.bluetoothDevice
        viewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        val root = binding.root

        val recyclerView = binding.recyclerviewMusic
        val adapter = MusicAdapter()
        recyclerView.adapter = adapter
        viewModel.texts.observe(viewLifecycleOwner) { adapter.submitList(it) }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            return MusicViewHolder(binding).apply { cardView.setOnClickListener {
                val wrapper = ContextThemeWrapper(it.context, R.style.Theme_SipKip_PopupOverlay)
                val popup = PopupMenu(wrapper, it)
                // Inflating the Popup using xml file
                popup.menuInflater.inflate(R.menu.item_music_options, popup.menu)

                // Registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener { item ->
                    Toast.makeText(it.context, "You clicked: ${item.title} of item: ${it.contentDescription}", Toast.LENGTH_SHORT).show()

                    true
                }

                popup.show() // Sowing popup menu
            }}
        }

        override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
            val item = getItem(position)
            holder.textView.text = item.displayPath
            holder.imageView.setImageDrawable(
                ResourcesCompat.getDrawable(holder.imageView.resources, item.drawable, null)
            )
            // Use the content description to hold the full path.
            holder.cardView.contentDescription = item.fullPath
        }
    }

    class MusicViewHolder(binding: ItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val imageView: ImageView = binding.imageViewItemMusic
        val textView: TextView = binding.textViewItemMusic
        val cardView: CardView = binding.cardViewItemMusic
    }
}