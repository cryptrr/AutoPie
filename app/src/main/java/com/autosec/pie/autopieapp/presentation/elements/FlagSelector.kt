package com.autopi.autopieapp.presentation.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopi.autopieapp.data.CommandExtra


@Composable
fun FlagSelector(extra: CommandExtra, checked: Boolean, onCheckedChange: (Boolean) -> Unit){
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(0.29F)).padding(vertical = 10.dp, horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically){
        Column(Modifier.fillMaxWidth(0.8F)){
            Text(extra.name, fontWeight = FontWeight.SemiBold)
            if(extra.description.isNotEmpty()) Text(text = extra.description, fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }
        Spacer(Modifier.width(3.dp))
        Box(Modifier.padding(1.dp)){
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}