export interface OrderDetails {
  orderId: string
  price: number
  quantity: number
  status: "PENDING" | "FILLED" | "PARTIALLY_FILLED" | "CANCELLED"
}

export interface TakeProfitOrder extends OrderDetails {
  targetLevel: number // 1, 2, 3, or 4
}

export interface Signal {
  id: string
  pair: string
  action: "BUY" | "SELL"
  entry: number
  stopLoss: number
  takeProfit: number[]
  timestamp: Date
  source: string
  status: "active" | "completed" | "cancelled"
  leverage?: number
  // Extended fields for detailed order tracking
  entryOrderId?: string
  entryOrderStatus?: "PENDING" | "FILLED" | "PARTIALLY_FILLED" | "CANCELLED"
  entryQuantity?: number
  stopLossOrder?: OrderDetails
  takeProfitOrders?: TakeProfitOrder[]
  account?: string
  unrealizedPnL?: number
  unrealizedPnLPercent?: number
}

export interface TakeProfitLevel {
  orderId: string        // Binance order ID
  price: number
  quantity: number
  percentage?: number    // Exit percentage for this level
  status: "PENDING" | "FILLED" | "PARTIALLY_FILLED" | "CANCELLED"
}

export interface Position {
  id: string
  pair: string
  side: "LONG" | "SHORT"
  entryPrice: number
  currentPrice: number
  quantity: number
  leverage: number
  pnl: number
  pnlPercentage: number
  stopLoss?: number
  stopLossOrderId?: string  // Binance SL order ID
  takeProfit?: number  // Legacy: Keep for backward compatibility
  takeProfitLevels?: TakeProfitLevel[]  // Multiple TP levels
  openedAt: Date
  binanceSymbol?: string  // Binance symbol format (BTCUSDT)
  positionId?: string    // Binance position identifier
}

export interface Trade {
  pair: string
  side: "LONG" | "SHORT"
  price: number
  quantity: number
  leverage: number
  stopLoss?: number
  takeProfit?: number
  takeProfitLevels?: TakeProfitLevel[]
}

export interface TradeHistory {
  id: string
  pair: string
  side: "LONG" | "SHORT"
  entryPrice: number
  exitPrice: number
  quantity: number
  leverage: number
  totalValue: number
  pnl: number
  pnlPercentage: number
  openedAt: Date
  closedAt: Date
}
