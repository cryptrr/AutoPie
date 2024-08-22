package com.autosec.pie.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ObserverConfigNotAvailableScreen() {
    Column(Modifier.padding(10.dp)){
        Text(
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 50.sp,
            text = "You need to create an observer config.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            shape = RoundedCornerShape(15.dp),
            onClick = {

        }) {
            Text(text = "See Instructions")
        }
        Spacer(modifier = Modifier.height(37.dp))
        Text(
            fontSize = 30.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Bold,
            text = "You can also create a share config.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            shape = RoundedCornerShape(15.dp),
            onClick = {

        }) {
            Text(text = "See Instructions")
        }
    }
}