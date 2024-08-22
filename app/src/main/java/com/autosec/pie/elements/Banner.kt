package com.autosec.pie.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosec.pie.domain.Notification
import com.autosec.pie.ui.theme.PastelBlue
import com.autosec.pie.ui.theme.PastelGreen
import com.autosec.pie.ui.theme.PastelRed
import com.autosec.pie.ui.theme.PastelYellow
import com.autosec.pie.ui.theme.Purple10
import com.autosec.pie.ui.theme.Purple50
import com.autosec.pie.viewModels.MainViewModel
import org.koin.java.KoinJavaComponent

sealed class BannerType {
    object Info : BannerType()
    object Warning : BannerType()
    object Success : BannerType()
    object Error : BannerType()

    val tintColor: Color
        get() = when (this) {
            is Info -> Purple50
            is Success -> PastelGreen
            is Warning -> PastelYellow
            is Error -> PastelRed
        }

    val icon: ImageVector
        get() = when (this) {
            is Info -> Icons.Rounded.Info
            is Success -> Icons.Filled.CheckCircle
            is Warning -> Icons.Rounded.Warning
            is Error -> Icons.Filled.Error
        }
}


@Composable
fun Banner(banner: Notification) {


    val viewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)


    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(75.dp)
            .clip(
                RoundedCornerShape(15.dp)
            )
            .background(banner.type.tintColor)
            .clickable(onClickLabel = "Clear All Notification Banners", role = Role.Image) {
                viewModel.clearAllBanners()
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(16f)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            )
            {
                Icon(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxSize(),
                    imageVector = banner.type.icon,
                    contentDescription = banner.title,
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(1.dp))
            Column(
                modifier = Modifier
                    .fillMaxHeight(), verticalArrangement = Arrangement.Center
            ) {
                Text(banner.title, fontSize = 15.sp, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    banner.description ?: "Unknown Error Occurred",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 18.sp,
                    maxLines = 2,
                )
            }
        }
        if (banner.hasAction) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(5f),
                contentAlignment = Alignment.Center
            )
            {
                banner.actionButton()
            }
        }

    }
}