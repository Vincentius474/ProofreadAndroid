package com.proofread.fixer

enum class Action(val label: String, val usesAi: Boolean) {
    FIX("Fix grammar", false),
    FORMAL("Make formal", true),
    SHORTER("Make shorter", true),
    FRIENDLIER("Make friendlier", true),
    PERSUASIVE("Make persuasive", true),
    TRANSLATE("Translate to English", true),
    EMAIL("Write as email", true);

    companion object {
        fun fromName(name: String?): Action = values().firstOrNull { it.name == name } ?: FIX
    }
}
