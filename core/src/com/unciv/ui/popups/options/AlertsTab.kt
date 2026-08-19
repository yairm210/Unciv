package com.unciv.ui.popups.options

internal class AlertsTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    override fun lateInitialize() {
        defaults().pad(2.5f)

        addHeader("Diplomacy")
        addCheckbox("Alert when a civilization declares war on another civilization", settings::alertRivalWarDeclaration)
        addCheckbox("Alert when two civilizations sign a peace treaty", settings::alertRivalPeaceTreaty)

        addHeader("Buildings")
        addCheckbox("Alert when a National Wonder becomes available to build", settings::alertNationalWonderAvailable)

        addHeader("Victory")
        addCheckbox("Alert when a rival civilization adds a Spaceship part to their capital", settings::alertRivalSpaceshipPart)

        super.lateInitialize()
    }
}
