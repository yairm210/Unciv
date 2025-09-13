package com.unciv.logic.trade

/** Enum that classifies Trade Types
 * @param numberType How the value number is formatted - None, Simple number or with a Gold symbol
 * @param isImmediate  Trade is a one-time effect without duration
 */
@Suppress("EnumEntryName")  // We do want the underscores in our names
enum class TradeOfferType(val numberType: TradeTypeNumberType, val isImmediate: Boolean) {
    Embassy              (TradeTypeNumberType.None, true),
    Gold                (TradeTypeNumberType.Gold, true),
    Gold_Per_Turn       (TradeTypeNumberType.Gold, false),
    /** Treaties are shared by both sides - like peace treaty and defensive pact */
    Treaty              (TradeTypeNumberType.None, false),
    /** Agreements are one-sided, like open borders */
    Agreement           (TradeTypeNumberType.Simple, false),
    Luxury_Resource     (TradeTypeNumberType.Simple, false),
    Strategic_Resource  (TradeTypeNumberType.Simple, false),
    Stockpiled_Resource  (TradeTypeNumberType.Simple, true),
    Technology          (TradeTypeNumberType.None, true),
    Introduction        (TradeTypeNumberType.None, true),
    WarDeclaration      (TradeTypeNumberType.None, true),
    PeaceProposal      (TradeTypeNumberType.None, true),
    City                (TradeTypeNumberType.None, true);
    
    enum class TradeTypeNumberType { None, Simple, Gold }
}
