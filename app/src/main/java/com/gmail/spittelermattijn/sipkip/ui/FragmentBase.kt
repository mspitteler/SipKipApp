package com.gmail.spittelermattijn.sipkip.ui

import androidx.fragment.app.Fragment
import androidx.navigation.NavArgs

abstract class FragmentBase : Fragment() {
    abstract val viewModel: ViewModelBase
    abstract val args: NavArgs
}