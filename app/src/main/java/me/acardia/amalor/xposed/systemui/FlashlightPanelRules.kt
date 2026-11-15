package me.acardia.amalor.xposed.systemui

internal object FlashlightPanelRules {
    fun adjustable(defaultLevel: Int?, maximum: Int?): Boolean =
        maximum != null && maximum > 1 && defaultLevel != null && defaultLevel in 1..maximum

    fun canOpen(enabled: Boolean, locked: Boolean, limited: Boolean, superPowerSave: Boolean): Boolean =
        enabled && !locked && !limited && !superPowerSave

    fun blockLevel(level: Int, locked: Boolean, limited: Boolean, superPowerSave: Boolean): Boolean =
        level != 0 && (locked || limited || superPowerSave)
}
