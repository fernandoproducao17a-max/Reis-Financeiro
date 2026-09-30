package com.reis.financeiro.data

data class Category(
    val name: String,
    val emoji: String
)

object DefaultCategories {
    val all = listOf(
        Category("Combustível", "⛽"),
        Category("Mercado", "🛒"),
        Category("Moradia", "🏠"),
        Category("Energia", "💡"),
        Category("Água", "💧"),
        Category("Internet", "🌐"),
        Category("Transporte", "🚗"),
        Category("Alimentação", "🍽️"),
        Category("Saúde", "💊"),
        Category("Educação", "📚"),
        Category("Lazer", "🎮"),
        Category("Salário", "💰"),
        Category("Freelance", "💼"),
        Category("Outros", "📌")
    )
}
