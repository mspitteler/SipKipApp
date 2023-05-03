package com.gmail.spittelermattijn.sipkip.ui.finddevice

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gmail.spittelermattijn.sipkip.databinding.FragmentFindDeviceBinding

class FindDeviceFragment : Fragment() {

    private var _binding: FragmentFindDeviceBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val findDeviceViewModel =
            ViewModelProvider(this)[FindDeviceViewModel::class.java]

        _binding = FragmentFindDeviceBinding.inflate(inflater, container, false)

        val root: View = binding.root

        val textView: TextView = binding.progressText
        findDeviceViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        val progressBar: ProgressBar = binding.progressBar
        findDeviceViewModel.progress.observe(viewLifecycleOwner) {
            progressBar.progress = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}