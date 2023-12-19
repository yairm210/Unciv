package com.unciv.models.personality

class DiplomaticPersonality {
    //This is not necessarily directly proportional to the chance of declaring war
    //halving may or may not double war rates. But changing it in the range of 17-25 should be fine
    var minimumWarMotivation = 23f
    //Same for this one, may or may not be directly proportional
    var minimumFriendshipMotivation = 0f
    //Only works for trade with humans due to the way the AI proposes trades
    var tradeModifier = 1f

    var openBordersChance = 30
    var defensivePactChance = 30
}
