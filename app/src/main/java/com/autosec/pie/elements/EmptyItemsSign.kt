package com.autosec.pie.elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun EmptyItemsBadge(icon: ImageVector, text: String){
    Box(
        Modifier
            .height(500.dp)
            .fillMaxWidth(), contentAlignment = Alignment.Center){
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Icon(
                modifier = Modifier
                    .padding(4.dp)
                    .size(100.dp),
                imageVector = icon,
                contentDescription = "Empty shares list",
                tint = Color.White.copy(0.4F)
            )
            Spacer(Modifier.height(15.dp))
            Text(text = text, color = Color.White.copy(0.7F), fontSize = 15.7.sp)
        }
    }
}