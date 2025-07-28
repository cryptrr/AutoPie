package com.autosec.pie.autopieapp.presentation.elements

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.viewModelScope
import com.autosec.pie.autopieapp.data.services.ForegroundService
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AutoPiePrimaryButton(
    text: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {

    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier
            .padding(vertical = 15.dp)
            .height(52.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20),
        //contentPadding = PaddingValues(vertical = 20.dp),
        onClick = {
            scope.launch {
                onClick()
            }
        },

        ) {


        Column {
            when (isLoading) {
                true -> {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp),
                        color = Color.Black.copy(alpha = 0.4F)
                    )
                }

                false -> {
                    Text(
                        text = text,
                        //modifier = Modifier.align(Alignment.Center),
                        letterSpacing = 1.11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AutoPieOutlinedButton(
    text: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {

    val scope = rememberCoroutineScope()

    OutlinedButton(
        modifier = Modifier
            .padding(vertical = 15.dp)
            .height(52.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20),
        //colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        //contentPadding = PaddingValues(vertical = 20.dp),
        onClick = {
            scope.launch {
                onClick()
            }
        },

        ) {


        Column {
            when (isLoading) {
                true -> {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp),
                        color = Color.Black.copy(alpha = 0.4F)
                    )
                }

                false -> {
                    Text(
                        text = text,
                        //modifier = Modifier.align(Alignment.Center),
                        letterSpacing = 1.11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun OutlinedButtonMedium(
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {

    val scope = rememberCoroutineScope()

    OutlinedButton(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20),
        //colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        //contentPadding = PaddingValues(vertical = 20.dp),
        onClick = {
            scope.launch {
                onClick()
            }
        },

        ) {


        Column {
            when (isLoading) {
                true -> {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp),
                        color = Color.Black.copy(alpha = 0.4F)
                    )
                }

                false -> {
                    Text(
                        text = text,
                        //modifier = Modifier.align(Alignment.Center),
                        letterSpacing = 1.11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}