package com.unciv.models.simulation

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.VictoryType
import java.time.Duration

class SimulationStep (var turns: Int = 0,
                      var victoryType: VictoryType = VictoryType.Neutral,
                      var duration: Duration = Duration.ZERO,
                      var winner: String? = null) {
}