package com.gmail.spittelermattijn.sipkip.ui.preferences

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceFragmentCompat
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface
import com.gmail.spittelermattijn.sipkip.ui.ViewModelBase

class PreferenceFragment : PreferenceFragmentCompat(), FragmentInterface {
    override lateinit var viewModel: ViewModelBase
    override val args by navArgs<PreferenceFragmentArgs>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewModel =
            ViewModelProvider(this)[PreferenceViewModel::class.java]
        addPreferencesFromResource(R.xml.preference_screen)
    }
}