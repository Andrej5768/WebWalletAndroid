package org.andrewdev.webwallet.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bed
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Bus
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.CarTaxiFront
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Handshake
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Laptop
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Percent
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Popcorn
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.ShoppingBasket
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sofa
import com.composables.icons.lucide.SquareParking
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.TrainFront
import com.composables.icons.lucide.TramFront
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Wine
import com.composables.icons.lucide.Wrench
import com.composables.icons.lucide.Zap

/**
 * The category icons by the names the backend stores (Lucide names in kebab case), one to one with the web's
 * `src/lib/categoryIcons.ts`. Goals use the same map. Unknown names fall back to a tag.
 */
object CategoryIcons {

    val byName: Map<String, ImageVector> = mapOf(
        "shopping-cart" to Lucide.ShoppingCart,
        "shopping-basket" to Lucide.ShoppingBasket,
        "utensils" to Lucide.Utensils,
        "coffee" to Lucide.Coffee,
        "package" to Lucide.Package,
        "wine" to Lucide.Wine,
        "popcorn" to Lucide.Popcorn,
        "house" to Lucide.House,
        "key-round" to Lucide.KeyRound,
        "zap" to Lucide.Zap,
        "droplet" to Lucide.Droplet,
        "wifi" to Lucide.Wifi,
        "wrench" to Lucide.Wrench,
        "sofa" to Lucide.Sofa,
        "car" to Lucide.Car,
        "car-taxi-front" to Lucide.CarTaxiFront,
        "bus" to Lucide.Bus,
        "tram-front" to Lucide.TramFront,
        "train-front" to Lucide.TrainFront,
        "fuel" to Lucide.Fuel,
        "square-parking" to Lucide.SquareParking,
        "plane" to Lucide.Plane,
        "bed" to Lucide.Bed,
        "heart-pulse" to Lucide.HeartPulse,
        "heart" to Lucide.Heart,
        "heart-handshake" to Lucide.HeartHandshake,
        "pill" to Lucide.Pill,
        "stethoscope" to Lucide.Stethoscope,
        "dumbbell" to Lucide.Dumbbell,
        "scissors" to Lucide.Scissors,
        "clapperboard" to Lucide.Clapperboard,
        "gamepad-2" to Lucide.Gamepad2,
        "tv" to Lucide.Tv,
        "repeat" to Lucide.Repeat,
        "music" to Lucide.Music,
        "book-open" to Lucide.BookOpen,
        "shirt" to Lucide.Shirt,
        "shopping-bag" to Lucide.ShoppingBag,
        "laptop" to Lucide.Laptop,
        "smartphone" to Lucide.Smartphone,
        "gift" to Lucide.Gift,
        "baby" to Lucide.Baby,
        "paw-print" to Lucide.PawPrint,
        "graduation-cap" to Lucide.GraduationCap,
        "receipt" to Lucide.Receipt,
        "percent" to Lucide.Percent,
        "shield" to Lucide.Shield,
        "calendar-check" to Lucide.CalendarCheck,
        "briefcase" to Lucide.Briefcase,
        "coins" to Lucide.Coins,
        "trending-up" to Lucide.TrendingUp,
        "tag" to Lucide.Tag,
        "undo-2" to Lucide.Undo2,
        "piggy-bank" to Lucide.PiggyBank,
        "landmark" to Lucide.Landmark,
        "wallet" to Lucide.Wallet,
    )

    val fallback: ImageVector = Lucide.Tag

    /** The icon of a category without one: a dashed tile with a question mark, drawn by `CategoryIcon` in M1. */
    val uncategorised: ImageVector = Lucide.CircleHelp

    operator fun get(name: String?): ImageVector = name?.let(byName::get) ?: fallback
}

/** Icons of the account types (docs/05 §4). */
object AccountTypeIcons {
    val card: ImageVector = Lucide.CreditCard
    val creditCard: ImageVector = Lucide.CreditCard
    val cash: ImageVector = Lucide.Banknote
    val savings: ImageVector = Lucide.PiggyBank
    val deposit: ImageVector = Lucide.Landmark
    val loan: ImageVector = Lucide.HandCoins
    val debt: ImageVector = Lucide.Handshake
    val investment: ImageVector = Lucide.TrendingUp
    val fop: ImageVector = Lucide.Briefcase
}

/** Icons of the five tabs of the main scaffold (docs/05 §4). */
object TabIcons {
    val overview: ImageVector = Lucide.LayoutDashboard
    val transactions: ImageVector = Lucide.ArrowLeftRight
    val budget: ImageVector = Lucide.Wallet
    val accounts: ImageVector = Lucide.Landmark
    val more: ImageVector = Lucide.Menu
}
