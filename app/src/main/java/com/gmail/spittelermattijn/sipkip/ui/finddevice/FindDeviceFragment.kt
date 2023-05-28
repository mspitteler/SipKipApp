package com.gmail.spittelermattijn.sipkip.ui.finddevice

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.gmail.spittelermattijn.sipkip.FindDeviceActivity
import com.gmail.spittelermattijn.sipkip.Preferences
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.databinding.FragmentFindDeviceBinding

class FindDeviceFragment : Fragment() {

    private var _binding: FragmentFindDeviceBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var deviceNameChanged = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val findDeviceViewModel =
            ViewModelProvider(this)[FindDeviceViewModel::class.java]

        _binding = FragmentFindDeviceBinding.inflate(inflater, container, false)
        Preferences.registerOnChangeListener { key, `val` ->
            if (key == R.string.bluetooth_device_name_key)
                deviceNameChanged = true
        }

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

    override fun onResume() {
        super.onResume()
        if (deviceNameChanged) {
            deviceNameChanged = false
            // TODO: Do this a more elegant way.
            startActivity(Intent(activity, FindDeviceActivity::class.java))
            activity?.finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}