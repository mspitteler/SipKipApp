package com.gmail.spittelermattijn.sipkip.ui.learn

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.gmail.spittelermattijn.sipkip.databinding.FragmentLearnBinding
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface

// TODO: Implement the learn fragment.
class LearnFragment : Fragment(), FragmentInterface {
    override lateinit var viewModel: LearnViewModel
    private lateinit var bluetoothDevice: BluetoothDevice

    private var _binding: FragmentLearnBinding? = null

    override val args by navArgs<LearnFragmentArgs>()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bluetoothDevice = args.bluetoothDevice
        viewModel = ViewModelProvider(this)[LearnViewModel::class.java]

        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textLearn
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