export interface Signal {
  id: string
  pair: string
  action: "BUY" | "SELL"
  entry: number
  stopLoss: number
  takeProfit: number // Single TP calculated by Python backend
  timestamp: Date
  source: string
  status: "active" | "completed" | "cancelled"
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
  takeProfit?: number
  openedAt: Date // Ensuring openedAt is documented for filtering
}

export interface Trade {
  pair: string
  side: "LONG" | "SHORT"
  price: number
  quantity: number
  leverage: number
  stopLoss?: number
  takeProfit?: number
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
