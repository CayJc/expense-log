package com.spacecay.expenselog.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon registry: categories and wallets store a string key so icons survive backups. */
object AppIcons {
    val map: LinkedHashMap<String, ImageVector> = linkedMapOf(
        // food & drink
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "cafe" to Icons.Filled.LocalCafe,
        "coffee" to Icons.Filled.Coffee,
        "pizza" to Icons.Filled.LocalPizza,
        "bakery" to Icons.Filled.BakeryDining,
        "drink" to Icons.Filled.LocalDrink,
        "bar" to Icons.Filled.LocalBar,
        "liquor" to Icons.Filled.Liquor,
        "grocery" to Icons.Filled.LocalGroceryStore,
        "cart" to Icons.Filled.ShoppingCart,
        // transport
        "bus" to Icons.Filled.DirectionsBus,
        "taxi" to Icons.Filled.LocalTaxi,
        "car" to Icons.Filled.DirectionsCar,
        "fuel" to Icons.Filled.LocalGasStation,
        "train" to Icons.Filled.Train,
        "motorbike" to Icons.Filled.TwoWheeler,
        "flight" to Icons.Filled.Flight,
        "parking" to Icons.Filled.LocalParking,
        // home & bills
        "home" to Icons.Filled.Home,
        "electricity" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "smartphone" to Icons.Filled.Smartphone,
        "tv" to Icons.Filled.Tv,
        "laundry" to Icons.Filled.LocalLaundryService,
        "repair" to Icons.Filled.Build,
        "construction" to Icons.Filled.Construction,
        "lightbulb" to Icons.Filled.Lightbulb,
        "receipt" to Icons.Filled.Receipt,
        "description" to Icons.Filled.Description,
        "subscriptions" to Icons.Filled.Subscriptions,
        // shopping & personal
        "shopping" to Icons.Filled.ShoppingBag,
        "mall" to Icons.Filled.LocalMall,
        "clothes" to Icons.Filled.Checkroom,
        "haircut" to Icons.Filled.ContentCut,
        "spa" to Icons.Filled.Spa,
        "diamond" to Icons.Filled.Diamond,
        "watch" to Icons.Filled.Watch,
        "headphones" to Icons.Filled.Headphones,
        "computer" to Icons.Filled.Computer,
        "store" to Icons.Filled.Storefront,
        "sell" to Icons.Filled.Sell,
        // health & family
        "hospital" to Icons.Filled.LocalHospital,
        "medicine" to Icons.Filled.Medication,
        "fitness" to Icons.Filled.FitnessCenter,
        "child" to Icons.Filled.ChildCare,
        "family" to Icons.Filled.FamilyRestroom,
        "elderly" to Icons.Filled.Elderly,
        "pets" to Icons.Filled.Pets,
        "favorite" to Icons.Filled.Favorite,
        // fun & learning
        "school" to Icons.Filled.School,
        "movie" to Icons.Filled.Movie,
        "games" to Icons.Filled.SportsEsports,
        "sports" to Icons.Filled.SportsSoccer,
        "hotel" to Icons.Filled.Hotel,
        "beach" to Icons.Filled.BeachAccess,
        "park" to Icons.Filled.Park,
        "brush" to Icons.Filled.Brush,
        "palette" to Icons.Filled.Palette,
        "celebration" to Icons.Filled.Celebration,
        "gift" to Icons.Filled.CardGiftcard,
        "redeem" to Icons.Filled.Redeem,
        "charity" to Icons.Filled.VolunteerActivism,
        "flower" to Icons.Filled.LocalFlorist,
        "star" to Icons.Filled.Star,
        // money
        "salary" to Icons.Filled.Payments,
        "work" to Icons.Filled.Work,
        "money" to Icons.Filled.AttachMoney,
        "coins" to Icons.Filled.MonetizationOn,
        "savings" to Icons.Filled.Savings,
        "bank" to Icons.Filled.AccountBalance,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "card" to Icons.Filled.CreditCard,
        "atm" to Icons.Filled.LocalAtm,
        "exchange" to Icons.Filled.CurrencyExchange,
        "percent" to Icons.Filled.Percent,
        "handshake" to Icons.Filled.Handshake,
        "security" to Icons.Filled.Security,
        "loyalty" to Icons.Filled.Loyalty,
        "category" to Icons.Filled.Category,
        "more" to Icons.Filled.MoreHoriz,
    )

    fun get(key: String?): ImageVector = map[key] ?: Icons.Filled.Category
}

/** Color palette for categories, wallets, budgets, goals. ARGB ints. */
object Palette {
    val colors: List<Int> = listOf(
        0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2, 0xFF5C6BC0, 0xFF2F5BEA,
        0xFF29B6F6, 0xFF26C6DA, 0xFF26A69A, 0xFF2E9E6A, 0xFF9CCC65, 0xFFD4C21F,
        0xFFFFCA28, 0xFFFFA726, 0xFFFF7043, 0xFF8D6E63, 0xFF78909C, 0xFF455A64,
    ).map { it.toInt() }

    fun at(i: Int) = colors[((i % colors.size) + colors.size) % colors.size]
}
