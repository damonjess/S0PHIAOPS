package com.sophia.ops.ui.devices

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DeviceIcon(
    name: String?,
    modifier: Modifier = Modifier
) {
    val icon = when {

        name?.contains("watch", true) == true ->
            Icons.Default.Watch

        name?.contains("galaxy", true) == true ->
            Icons.Default.PhoneAndroid

        name?.contains("pixel", true) == true ->
            Icons.Default.PhoneAndroid

        name?.contains("iphone", true) == true ->
            Icons.Default.PhoneIphone

        name?.contains("buds", true) == true ->
            Icons.Default.Headphones

        name?.contains("airpods", true) == true ->
            Icons.Default.Headphones

        name?.contains("speaker", true) == true ->
            Icons.Default.Speaker

        name?.contains("laptop", true) == true ->
            Icons.Default.Laptop

        name?.contains("pc", true) == true ->
            Icons.Default.Computer

        name?.contains("duoek", true) == true ->
            Icons.Default.MonitorHeart

        else ->
            Icons.Default.Bluetooth
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(32.dp),
        tint = Color(0xFF00FF66)
    )
}
