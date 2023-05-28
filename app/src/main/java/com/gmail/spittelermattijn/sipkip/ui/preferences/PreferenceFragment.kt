package com.gmail.spittelermattijn.sipkip.ui.preferences

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.gmail.spittelermattijn.sipkip.R
import com.gmail.spittelermattijn.sipkip.ui.FragmentInterface

class PreferenceFragment : PreferenceFragmentCompat(), FragmentInterface {
    override lateinit var viewModel: PreferenceViewModel
    override val args by navArgs<PreferenceFragmentArgs>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewModel =
            ViewModelProvider(this)[PreferenceViewModel::class.java]
        addPreferencesFromResource(R.xml.preference_screen)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is DialogPreference -> showDialogPreference(preference)
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    @Suppress("Deprecation")
    private fun showDialogPreference(preference: DialogPreference) {
        val dialogFragment = when (preference) {
            is EditTextPreference -> MaterialEditTextPreference()
            is ListPreference -> MaterialListPreference()
            else -> null
        }
        val bundle = Bundle(1)
        bundle.putString("key", preference.key)
        dialogFragment?.arguments = bundle
        dialogFragment?.setTargetFragment(this, 0)
        dialogFragment?.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    }
}