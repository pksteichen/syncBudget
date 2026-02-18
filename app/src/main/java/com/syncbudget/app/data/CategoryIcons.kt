package com.syncbudget.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

val CATEGORY_ICON_MAP: Map<String, ImageVector> = linkedMapOf(
    // Food & Drink
    "Restaurant" to Icons.Filled.Restaurant,
    "Fastfood" to Icons.Filled.Fastfood,
    "LocalCafe" to Icons.Filled.LocalCafe,
    "LocalBar" to Icons.Filled.LocalBar,
    "BakeryDining" to Icons.Filled.BakeryDining,
    "LocalPizza" to Icons.Filled.LocalPizza,
    "LunchDining" to Icons.Filled.LunchDining,
    "LocalGroceryStore" to Icons.Filled.LocalGroceryStore,
    "ShoppingCart" to Icons.Filled.ShoppingCart,
    "Icecream" to Icons.Filled.Icecream,
    "SetMeal" to Icons.Filled.SetMeal,
    "RamenDining" to Icons.Filled.RamenDining,
    "DinnerDining" to Icons.Filled.DinnerDining,
    "LocalDining" to Icons.Filled.LocalDining,
    "Liquor" to Icons.Filled.Liquor,
    "SportsBar" to Icons.Filled.SportsBar,
    "Cake" to Icons.Filled.Cake,
    "TakeoutDining" to Icons.Filled.TakeoutDining,

    // Transport
    "DirectionsCar" to Icons.Filled.DirectionsCar,
    "LocalGasStation" to Icons.Filled.LocalGasStation,
    "DirectionsBus" to Icons.Filled.DirectionsBus,
    "Train" to Icons.Filled.Train,
    "Flight" to Icons.Filled.Flight,
    "DirectionsBike" to Icons.AutoMirrored.Filled.DirectionsBike,
    "LocalTaxi" to Icons.Filled.LocalTaxi,
    "TwoWheeler" to Icons.Filled.TwoWheeler,
    "LocalShipping" to Icons.Filled.LocalShipping,
    "LocalParking" to Icons.Filled.LocalParking,
    "Sailing" to Icons.Filled.Sailing,

    // Home & Utilities
    "Home" to Icons.Filled.Home,
    "Apartment" to Icons.Filled.Apartment,
    "Build" to Icons.Filled.Build,
    "ElectricalServices" to Icons.Filled.ElectricalServices,
    "Water" to Icons.Filled.Water,
    "Wifi" to Icons.Filled.Wifi,
    "Phone" to Icons.Filled.Phone,
    "CleaningServices" to Icons.Filled.CleaningServices,
    "Cottage" to Icons.Filled.Cottage,
    "Roofing" to Icons.Filled.Roofing,
    "Plumbing" to Icons.Filled.Plumbing,
    "Chair" to Icons.Filled.Chair,
    "Bed" to Icons.Filled.Bed,
    "Shower" to Icons.Filled.Shower,
    "Bathtub" to Icons.Filled.Bathtub,
    "Light" to Icons.Filled.Light,
    "AcUnit" to Icons.Filled.AcUnit,
    "Thermostat" to Icons.Filled.Thermostat,

    // Health & Wellness
    "LocalHospital" to Icons.Filled.LocalHospital,
    "MedicalServices" to Icons.Filled.MedicalServices,
    "Medication" to Icons.Filled.Medication,
    "FitnessCenter" to Icons.Filled.FitnessCenter,
    "Spa" to Icons.Filled.Spa,
    "Favorite" to Icons.Filled.Favorite,
    "SelfImprovement" to Icons.Filled.SelfImprovement,
    "LocalPharmacy" to Icons.Filled.LocalPharmacy,

    // Education & Work
    "School" to Icons.Filled.School,
    "MenuBook" to Icons.AutoMirrored.Filled.MenuBook,
    "Work" to Icons.Filled.Work,
    "Computer" to Icons.Filled.Computer,
    "Laptop" to Icons.Filled.Laptop,
    "Smartphone" to Icons.Filled.Smartphone,
    "Headphones" to Icons.Filled.Headphones,

    // Entertainment
    "SportsEsports" to Icons.Filled.SportsEsports,
    "Movie" to Icons.Filled.Movie,
    "MusicNote" to Icons.Filled.MusicNote,
    "Tv" to Icons.Filled.Tv,
    "SportsBasketball" to Icons.Filled.SportsBasketball,
    "SportsSoccer" to Icons.Filled.SportsSoccer,
    "TheaterComedy" to Icons.Filled.TheaterComedy,
    "Casino" to Icons.Filled.Casino,
    "Pool" to Icons.Filled.Pool,
    "GolfCourse" to Icons.Filled.GolfCourse,
    "Hiking" to Icons.Filled.Hiking,
    "Snowboarding" to Icons.Filled.Snowboarding,
    "Nightlife" to Icons.Filled.Nightlife,
    "Celebration" to Icons.Filled.Celebration,
    "Balloon" to Icons.Filled.Celebration,

    // Photography & Art
    "CameraAlt" to Icons.Filled.CameraAlt,
    "Brush" to Icons.Filled.Brush,
    "Palette" to Icons.Filled.Palette,

    // Shopping & Personal
    "ShoppingBag" to Icons.Filled.ShoppingBag,
    "Checkroom" to Icons.Filled.Checkroom,
    "Watch" to Icons.Filled.Watch,
    "CardGiftcard" to Icons.Filled.CardGiftcard,
    "Diamond" to Icons.Filled.Diamond,
    "Storefront" to Icons.Filled.Storefront,
    "Store" to Icons.Filled.Store,
    "LocalMall" to Icons.Filled.LocalMall,
    "LocalOffer" to Icons.Filled.LocalOffer,

    // Pets & Nature
    "Pets" to Icons.Filled.Pets,
    "Park" to Icons.Filled.Park,
    "Forest" to Icons.Filled.Forest,
    "LocalFlorist" to Icons.Filled.LocalFlorist,
    "Grass" to Icons.Filled.Grass,
    "WbSunny" to Icons.Filled.WbSunny,
    "Cloud" to Icons.Filled.Cloud,

    // Finance
    "AccountBalance" to Icons.Filled.AccountBalance,
    "Savings" to Icons.Filled.Savings,
    "CreditCard" to Icons.Filled.CreditCard,
    "AttachMoney" to Icons.Filled.AttachMoney,
    "Receipt" to Icons.Filled.Receipt,
    "Payments" to Icons.Filled.Payments,
    "LocalAtm" to Icons.Filled.LocalAtm,
    "RequestQuote" to Icons.Filled.RequestQuote,
    "Sync" to Icons.Filled.Sync,
    "Schedule" to Icons.Filled.Schedule,

    // Travel & Lodging
    "Hotel" to Icons.Filled.Hotel,
    "BeachAccess" to Icons.Filled.BeachAccess,
    "Luggage" to Icons.Filled.Luggage,

    // Security & Communication
    "Security" to Icons.Filled.Security,
    "Lock" to Icons.Filled.Lock,
    "Email" to Icons.Filled.Email,
    "Chat" to Icons.AutoMirrored.Filled.Chat,

    // Construction & Services
    "Construction" to Icons.Filled.Construction,

    // Misc
    "ChildCare" to Icons.Filled.ChildCare,
    "Elderly" to Icons.Filled.Elderly,
    "VolunteerActivism" to Icons.Filled.VolunteerActivism,
    "Handyman" to Icons.Filled.Handyman,
    "LocalLaundryService" to Icons.Filled.LocalLaundryService,
    "Star" to Icons.Filled.Star,
    "Flag" to Icons.Filled.Flag,
    "Bookmark" to Icons.Filled.Bookmark,
    "LocalLibrary" to Icons.Filled.LocalLibrary,
    "EmojiEvents" to Icons.Filled.EmojiEvents,
    "Category" to Icons.Filled.Category,
    "MoreHoriz" to Icons.Filled.MoreHoriz
)

fun getCategoryIcon(iconName: String): ImageVector {
    return CATEGORY_ICON_MAP[iconName] ?: Icons.Filled.Category
}
