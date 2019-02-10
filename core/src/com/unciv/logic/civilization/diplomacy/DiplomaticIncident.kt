package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.trade.Trade

class DiplomaticIncident(val civName:String, val type: DiplomaticIncidentType, val trade: Trade?=null)