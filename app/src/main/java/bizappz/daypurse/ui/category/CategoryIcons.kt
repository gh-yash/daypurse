package bizappz.daypurse.ui.category

import androidx.annotation.DrawableRes
import bizappz.daypurse.R

/**
 * Maps stored category icon keys to drawables. Keys are persisted in the database, so never
 * rename an existing key; add new ones (and list them in scripts/fetch-icons.sh) instead.
 */
object CategoryIcons {
    val all: Map<String, Int> = linkedMapOf(
        "account_balance_wallet" to R.drawable.ic_account_balance_wallet,
        "restaurant" to R.drawable.ic_restaurant,
        "shopping_cart" to R.drawable.ic_shopping_cart,
        "directions_bus" to R.drawable.ic_directions_bus,
        "local_gas_station" to R.drawable.ic_local_gas_station,
        "shopping_bag" to R.drawable.ic_shopping_bag,
        "receipt" to R.drawable.ic_receipt,
        "movie" to R.drawable.ic_movie,
        "medical_services" to R.drawable.ic_medical_services,
        "local_cafe" to R.drawable.ic_local_cafe,
        "spa" to R.drawable.ic_spa,
        "school" to R.drawable.ic_school,
        "redeem" to R.drawable.ic_redeem,
        "category" to R.drawable.ic_category,
        "home" to R.drawable.ic_home,
        "flight" to R.drawable.ic_flight,
        "pets" to R.drawable.ic_pets,
        "fitness_center" to R.drawable.ic_fitness_center,
        "sports_esports" to R.drawable.ic_sports_esports,
        "smartphone" to R.drawable.ic_smartphone,
        "wifi" to R.drawable.ic_wifi,
        "child_care" to R.drawable.ic_child_care,
        "checkroom" to R.drawable.ic_checkroom,
        "local_bar" to R.drawable.ic_local_bar,
        "savings" to R.drawable.ic_savings,
        "work" to R.drawable.ic_work,
        "build" to R.drawable.ic_build,
        "local_taxi" to R.drawable.ic_local_taxi,
        "train" to R.drawable.ic_train,
        "two_wheeler" to R.drawable.ic_two_wheeler,
        "celebration" to R.drawable.ic_celebration,
        "volunteer_activism" to R.drawable.ic_volunteer_activism,
        "local_pharmacy" to R.drawable.ic_local_pharmacy,
        "local_laundry_service" to R.drawable.ic_local_laundry_service,
        "subscriptions" to R.drawable.ic_subscriptions,
        "payments" to R.drawable.ic_payments,
        "credit_card" to R.drawable.ic_credit_card,
        "account_balance" to R.drawable.ic_account_balance,
        "local_parking" to R.drawable.ic_local_parking,
        "lunch_dining" to R.drawable.ic_lunch_dining,
        "icecream" to R.drawable.ic_icecream,
        "bakery_dining" to R.drawable.ic_bakery_dining,
        "music_note" to R.drawable.ic_music_note,
        "menu_book" to R.drawable.ic_menu_book,
        "electric_bolt" to R.drawable.ic_electric_bolt,
        "water_drop" to R.drawable.ic_water_drop,
        "sports_soccer" to R.drawable.ic_sports_soccer,
        "travel_explore" to R.drawable.ic_travel_explore,
    )

    @DrawableRes
    fun forKey(key: String?): Int = all[key] ?: R.drawable.ic_category
}
