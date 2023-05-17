package com.gmail.spittelermattijn.sipkip.ui.music

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.findFragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.coroutineScope
import com.gmail.spittelermattijn.sipkip.databinding.FragmentMusicBinding
import com.gmail.spittelermattijn.sipkip.databinding.ItemMusicBinding
import com.gmail.spittelermattijn.sipkip.showFirstDirectoryPicker
import com.gmail.spittelermattijn.sipkip.showRenameEditText
import com.gmail.spittelermattijn.sipkip.ui.FragmentBase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch


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

        val swipeRefreshLayout = binding.swipeRefreshLayoutMusic
        swipeRefreshLayout.setOnRefreshListener {
            coroutineScope.launch {
                viewModel.update()
                swipeRefreshLayout.post { swipeRefreshLayout.isRefreshing = false }
            }
        }

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
                popup.setOnMenuItemClickListener(OnMenuItemClickListener(it.context, it.contentDescription.toString(), it.findFragment()))
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

    private class OnMenuItemClickListener(val context: Context, val fullPath: String, val fragment: MusicFragment) : PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            val path = fullPath.removePrefix(fragment.viewModel.littleFsPath)
            val firstDirectory = path.split('/').first { it.isNotEmpty() }
            when (item.itemId) {
                R.id.option_rename -> {
                    MaterialAlertDialogBuilder(context).showRenameEditText(path.replaceFirst("/*$firstDirectory/*".toRegex(), "")) {
                        Toast.makeText(context, context.getString(R.string.toast_rename, fullPath, it), Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            fragment.viewModel.renameItem(fullPath, "${fragment.viewModel.littleFsPath}/$firstDirectory/$it")
                            fragment.viewModel.update()
                        }
                    }
                }
                R.id.option_remove -> {
                    Toast.makeText(context, context.getString(R.string.toast_remove, fullPath), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        fragment.viewModel.removeItem(fullPath)
                        fragment.viewModel.update()
                    }
                }
                R.id.option_change_first_directory -> {
                    MaterialAlertDialogBuilder(context).showFirstDirectoryPicker(firstDirectory) {
                        Toast.makeText(context, context.getString(R.string.toast_change_first_directory, fullPath, it), Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            fragment.viewModel.changeItemFirstDirectory(fullPath, it)
                            fragment.viewModel.update()
                        }
                    }
                }
            }
            return true
        }
    }

    class MusicViewHolder(binding: ItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val imageView: ImageView = binding.imageViewItemMusic
        val textView: TextView = binding.textViewItemMusic
        val cardView: CardView = binding.cardViewItemMusic
    }
}