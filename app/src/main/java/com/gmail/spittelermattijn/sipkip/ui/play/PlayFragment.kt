package com.gmail.spittelermattijn.sipkip.ui.play

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.gmail.spittelermattijn.sipkip.databinding.FragmentPlayBinding
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface

// TODO: Implement the play fragment.
class PlayFragment : Fragment(), FragmentInterface {
    override lateinit var viewModel: PlayViewModel
    private lateinit var bluetoothDevice: BluetoothDevice

    private var _binding: FragmentPlayBinding? = null

    override val args by navArgs<PlayFragmentArgs>()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bluetoothDevice = args.bluetoothDevice
        viewModel =
            ViewModelProvider(this)[PlayViewModel::class.java]

        _binding = FragmentPlayBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textPlay
        viewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}