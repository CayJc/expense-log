package com.spacecay.expenselog.data

import com.spacecay.expenselog.util.Palette

object Seed {
    private data class C(val name: String, val icon: String, val color: Int, val subs: List<String> = emptyList())

    private val expense = listOf(
        C("Food & Dining", "restaurant", 0, listOf("Restaurant", "Fast food", "Delivery")),
        C("Groceries", "grocery", 9),
        C("Coffee & Snacks", "cafe", 15),
        C("Transport", "bus", 5, listOf("Taxi / Ride", "Bus", "Fuel", "Parking")),
        C("Rent", "home", 3),
        C("Utilities", "electricity", 12, listOf("Electricity", "Water")),
        C("Airtime & Data", "phone", 6, listOf("Airtime", "Data package")),
        C("Shopping", "shopping", 1),
        C("Clothing", "clothes", 2),
        C("Health", "hospital", 0, listOf("Pharmacy", "Doctor")),
        C("Education", "school", 4),
        C("Entertainment", "movie", 8),
        C("Family & Kids", "family", 13),
        C("Gifts & Donations", "gift", 14),
        C("Personal Care", "haircut", 7),
        C("Bills & Fees", "receipt", 16, listOf("Service fee", "Bank charge")),
        C("Travel", "flight", 6),
        C("Others", "more", 17),
    )

    private val income = listOf(
        C("Salary", "salary", 9),
        C("Business", "store", 5),
        C("YouTube & Freelance", "work", 0),
        C("Gifts received", "gift", 14),
        C("Interest", "percent", 12),
        C("Refunds", "exchange", 6),
        C("Other income", "more", 17),
    )

    suspend fun populateBook(dao: AppDao, bookId: Long, mainCurrency: String, withWallets: Boolean) {
        dao.upsertRate(CurrencyRate(bookId, mainCurrency, 1.0))
        var order = 0
        for ((type, list) in listOf(CatType.EXPENSE to expense, CatType.INCOME to income)) {
            for (c in list) {
                val color = Palette.at(c.color)
                val id = dao.insertCategory(Category(bookId = bookId, name = c.name, type = type, color = color, icon = c.icon, sortOrder = order++))
                c.subs.forEachIndexed { i, s ->
                    dao.insertCategory(Category(bookId = bookId, name = s, type = type, color = color, icon = c.icon, parentId = id, sortOrder = i))
                }
            }
        }
        if (withWallets) {
            dao.insertWallet(Wallet(bookId = bookId, name = "Cash", type = WalletType.CASH, currency = mainCurrency, color = Palette.at(9), icon = "money", sortOrder = 0))
            dao.insertWallet(Wallet(bookId = bookId, name = "telebirr", type = WalletType.MOBILE_MONEY, currency = mainCurrency, color = Palette.at(6), icon = "smartphone", sortOrder = 1))
            dao.insertWallet(Wallet(bookId = bookId, name = "CBE", type = WalletType.BANK, currency = mainCurrency, color = Palette.at(3), icon = "bank", sortOrder = 2))
        }
    }

    val payeeHints: Map<String, String> = mapOf(
        "airtime" to "Airtime",
        "topup" to "Airtime",
        "top up" to "Airtime",
        "recharge" to "Airtime",
        "bundle" to "Data package",
        "package" to "Data package",
        "data" to "Data package",
        "ethio telecom" to "Airtime & Data",
        "safaricom" to "Airtime & Data",
        "eeu" to "Electricity",
        "electric" to "Electricity",
        "water" to "Water",
        "fuel" to "Fuel",
        "total" to "Fuel",
        "noc" to "Fuel",
        "ride" to "Taxi / Ride",
        "feres" to "Taxi / Ride",
        "pharma" to "Pharmacy",
        "hospital" to "Health",
        "supermarket" to "Groceries",
        "market" to "Groceries",
        "cafe" to "Coffee & Snacks",
        "coffee" to "Coffee & Snacks",
        "restaurant" to "Restaurant",
        "hotel" to "Food & Dining",
        "salary" to "Salary",
        "school" to "Education",
        "university" to "Education",
    )
}
