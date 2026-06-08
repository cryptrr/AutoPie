package com.autosec.pie.autopieapp.presentation.elements

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderSelector(state: SliderState){
    Slider(state = state)
}