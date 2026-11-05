package com.anasikus.lifeassistant.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.ui.AppColors

@Composable
fun BottomNavigationBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit
) {
    NavigationBar(
        containerColor = AppColors.Card,
        contentColor = AppColors.MainText
    ) {
        AppDestination.values().forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = {
                    onDestinationSelected(destination)
                },
                icon = {
                    Text(
                        text = destination.symbol,
                        fontSize = 20.sp
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.MainText,
                    selectedTextColor = AppColors.Accent,
                    indicatorColor = AppColors.Accent.copy(alpha = 0.2f),
                    unselectedIconColor = AppColors.SecondaryText,
                    unselectedTextColor = AppColors.SecondaryText
                )
            )
        }
    }
}