package com.gmail.spittelermattijn.sipkip.ui

import androidx.navigation.NavArgs

interface FragmentInterface {
    val viewModel: ViewModelBase
    val args: NavArgs
}