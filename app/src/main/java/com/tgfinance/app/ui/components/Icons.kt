package com.tgfinance.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.tgfinance.core.model.AccountType

/**
 * Icons are stored by key (not by resource id) so that they survive sync and app updates.
 */
object FinanceIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "cart" to Icons.Outlined.ShoppingCart,
        "restaurant" to Icons.Outlined.Restaurant,
        "coffee" to Icons.Outlined.LocalCafe,
        "home" to Icons.Outlined.Home,
        "bolt" to Icons.Outlined.Bolt,
        "water" to Icons.Outlined.WaterDrop,
        "wifi" to Icons.Outlined.Wifi,
        "phone" to Icons.Outlined.PhoneAndroid,
        "car" to Icons.Outlined.DirectionsCar,
        "gas" to Icons.Outlined.LocalGasStation,
        "train" to Icons.Outlined.Train,
        "flight" to Icons.Outlined.Flight,
        "beach" to Icons.Outlined.BeachAccess,
        "health" to Icons.Outlined.LocalHospital,
        "fitness" to Icons.Outlined.FitnessCenter,
        "beauty" to Icons.Outlined.Spa,
        "clothes" to Icons.Outlined.Checkroom,
        "bag" to Icons.Outlined.ShoppingBag,
        "games" to Icons.Outlined.SportsEsports,
        "movie" to Icons.Outlined.Movie,
        "music" to Icons.Outlined.MusicNote,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "school" to Icons.Outlined.School,
        "subscriptions" to Icons.Outlined.Subscriptions,
        "shield" to Icons.Outlined.Shield,
        "receipt" to Icons.Outlined.Receipt,
        "pets" to Icons.Outlined.Pets,
        "child" to Icons.Outlined.ChildCare,
        "gift" to Icons.Outlined.CardGiftcard,
        "celebration" to Icons.Outlined.Celebration,
        "favorite" to Icons.Outlined.FavoriteBorder,
        "build" to Icons.Outlined.Build,
        "laptop" to Icons.Outlined.Laptop,
        "work" to Icons.Outlined.Work,
        "money" to Icons.Outlined.Payments,
        "trending" to Icons.AutoMirrored.Outlined.TrendingUp,
        "bank" to Icons.Outlined.AccountBalance,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "cash" to Icons.Outlined.LocalAtm,
        "card" to Icons.Outlined.CreditCard,
        "savings" to Icons.Outlined.Savings,
        "diamond" to Icons.Outlined.Diamond,
        "target" to Icons.Outlined.TrackChanges,
        "star" to Icons.Outlined.Star,
        "more" to Icons.Outlined.MoreHoriz,
    )

    fun forKey(key: String): ImageVector = all[key] ?: Icons.Outlined.MoreHoriz

    fun defaultFor(type: AccountType): String = when (type) {
        AccountType.CHECKING -> "bank"
        AccountType.CASH -> "cash"
        AccountType.SAVINGS -> "savings"
        AccountType.CREDIT_CARD -> "card"
        AccountType.INVESTMENT -> "trending"
        AccountType.OTHER -> "wallet"
    }
}
