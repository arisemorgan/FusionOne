package com.fusionone.app.feature.football.model

enum class TopLeague(val code: String, val displayName: String, val country: String) {
    PREMIER_LEAGUE("PL", "Premier League", "England"),
    LA_LIGA("PD", "La Liga", "Spain"),
    BUNDESLIGA("BL1", "Bundesliga", "Germany"),
    SERIE_A("SA", "Serie A", "Italy"),
    LIGUE_1("FL1", "Ligue 1", "France")
}
