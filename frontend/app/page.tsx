"use client"

import { useState, useEffect } from "react"
import { ProtectedRoute } from "@/components/protected-route"
import { TelegramSignals } from "@/components/telegram-signals"
import { PositionManagement } from "@/components/position-management"
import { ManualTrading } from "@/components/manual-trading"
import { TradeHistoryComponent } from "@/components/trade-history"
import { TradeManagement } from "@/components/trade-management"
import { Sidebar } from "@/components/sidebar"
import { AccountSummary } from "@/components/account-summary"
import { tradingAPI, signalsAPI } from "@/lib/api"
import { useAuth } from "@/contexts/auth-context"
import { Radio, TrendingUp, ArrowLeftRight, History, LogOut, User as UserIcon } from "lucide-react"
import type { Signal, Position, Trade, TradeHistory } from "@/types/trading"

type TimePeriod = "24h" | "7d" | "52W" | "All"

export default function CryptoPositionManagement() {
  const { user, logout } = useAuth()
  const [activeSection, setActiveSection] = useState("signals")
  const [selectedPeriod, setSelectedPeriod] = useState<TimePeriod>("All")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [signals, setSignals] = useState<Signal[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [tradeHistory, setTradeHistory] = useState<TradeHistory[]>([])

  // Load data (frontend-only mode - no authentication required)
  useEffect(() => {
    const loadData = async () => {
      setIsLoading(true)

      try {
        // Fetch trading signals from backend (if available)
        const fetchedSignals = await signalsAPI.getAllSignals()
        console.log("[OK] Signals fetched:", fetchedSignals)

        // Transform backend signal data to frontend Signal type
        const transformedSignals: Signal[] = fetchedSignals.map((signal: any) => ({
          id: signal.id.toString(),
          pair: signal.pair || "UNKNOWN",
          action: signal.setupType === "LONG" ? "BUY" : signal.setupType === "SHORT" ? "SELL" : "BUY",
          entry: signal.entry || 0,
          stopLoss: signal.stopLoss || 0,
          takeProfit: signal.takeProfit || 0, // Single TP calculated by Python backend
          timestamp: new Date(signal.timestamp),
          source: signal.channel || "Telegram",
          status: "active",
        }))

        setSignals(transformedSignals)

        // Fetch real-time active positions from Binance account
        // This includes positions opened via our app AND manually via Binance web/mobile
        const livePositions = await tradingAPI.getLivePositions()
        console.log("[OK] Live positions fetched from Binance:", livePositions)

        // Transform backend Trade data to frontend Position type
        const transformedPositions: Position[] = livePositions.map((trade: any) => ({
          id: trade.id?.toString() || `binance-${trade.pair}`,
          pair: trade.pair,
          side: trade.side === "BUY" ? "LONG" : trade.side === "SELL" ? "SHORT" : trade.side,
          entryPrice: trade.entryPrice || 0,
          currentPrice: trade.exitPrice || trade.entryPrice || 0, // exitPrice stores current market price
          quantity: trade.entryQuantity || 0,
          leverage: trade.leverage || 1,
          pnl: trade.pnl || 0,
          pnlPercentage: trade.pnlPercent || 0,
          stopLoss: trade.stopLoss,
          takeProfit: trade.takeProfit,
          openedAt: new Date(trade.openedAt || Date.now()),
        }))

        setPositions(transformedPositions)
        console.log(`[OK] Displaying ${transformedPositions.length} active positions`)
      } catch (apiErr) {
        console.log("Backend not available - running in frontend-only mode")
      }

      setIsLoading(false)
    }

    loadData()

    // Set up auto-refresh for positions every 10 seconds
    const refreshInterval = setInterval(async () => {
      try {
        const livePositions = await tradingAPI.getLivePositions()
        const transformedPositions: Position[] = livePositions.map((trade: any) => ({
          id: trade.id?.toString() || `binance-${trade.pair}`,
          pair: trade.pair,
          side: trade.side === "BUY" ? "LONG" : trade.side === "SELL" ? "SHORT" : trade.side,
          entryPrice: trade.entryPrice || 0,
          currentPrice: trade.exitPrice || trade.entryPrice || 0,
          quantity: trade.entryQuantity || 0,
          leverage: trade.leverage || 1,
          pnl: trade.pnl || 0,
          pnlPercentage: trade.pnlPercent || 0,
          stopLoss: trade.stopLoss,
          takeProfit: trade.takeProfit,
          openedAt: new Date(trade.openedAt || Date.now()),
        }))
        setPositions(transformedPositions)
        console.log(`[REFRESH] Updated ${transformedPositions.length} positions`)
      } catch (err) {
        console.log("Failed to refresh positions:", err)
      }
    }, 10000) // Refresh every 10 seconds

    // Cleanup interval on unmount
    return () => clearInterval(refreshInterval)
  }, [])

  // WebSocket connection disabled - will be enabled when backend is ready

  const handleAddPosition = async (trade: Trade) => {
    try {
      // Call backend to execute trade
      const result = await tradingAPI.executeTrade(trade)

      const newPosition: Position = {
        id: result.id || Date.now().toString(),
        pair: trade.pair,
        side: trade.side,
        entryPrice: trade.price,
        currentPrice: trade.price,
        quantity: trade.quantity,
        leverage: trade.leverage,
        pnl: 0,
        pnlPercentage: 0,
        stopLoss: trade.stopLoss,
        takeProfit: trade.takeProfit,
        openedAt: new Date(),
      }
      setPositions([...positions, newPosition])
    } catch (err) {
      console.error("Failed to execute trade:", err)
      setError("Failed to execute trade")
    }
  }

  const handleClosePosition = async (id: string) => {
    try {
      // Call backend to close position
      await tradingAPI.closePosition(id)

      const position = positions.find((p) => p.id === id)
      if (position) {
        const historyEntry: TradeHistory = {
          id: Date.now().toString(),
          pair: position.pair,
          side: position.side,
          entryPrice: position.entryPrice,
          exitPrice: position.currentPrice,
          quantity: position.quantity,
          leverage: position.leverage,
          totalValue: position.currentPrice * position.quantity * position.leverage,
          pnl: position.pnl,
          pnlPercentage: position.pnlPercentage,
          openedAt: position.openedAt,
          closedAt: new Date(),
        }
        setTradeHistory([historyEntry, ...tradeHistory])
      }
      setPositions(positions.filter((p) => p.id !== id))
    } catch (err) {
      console.error("Failed to close position:", err)
      setError("Failed to close position")
    }
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-foreground mx-auto mb-4"></div>
          <p className="text-muted-foreground">Loading trading data...</p>
        </div>
      </div>
    )
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen h-screen bg-background flex flex-col">
        {error && (
          <div className="bg-destructive/10 border border-destructive text-destructive px-3 py-2 sm:px-4 sm:py-3 rounded-md m-2 sm:m-4 text-xs sm:text-sm">
            <p className="font-medium">{error}</p>
            <button
              onClick={() => setError(null)}
              className="text-xs mt-2 underline hover:no-underline"
            >
              Dismiss
            </button>
          </div>
        )}

        <header className="border-b border-border bg-card shrink-0">
          <div className="px-3 sm:px-4 py-3 sm:py-4 flex items-center justify-between">
            <h1 className="text-lg sm:text-2xl font-bold text-foreground">Crypto Position Manager</h1>
            <div className="flex items-center gap-2 sm:gap-4">
              <div className="flex items-center gap-2 px-2 sm:px-3 py-1 sm:py-2 bg-secondary/50 rounded-lg">
                <UserIcon className="h-4 w-4 text-muted-foreground" />
                <span className="text-xs sm:text-sm font-medium">{user?.username}</span>
              </div>
              <button
                onClick={logout}
                className="flex items-center gap-1 sm:gap-2 px-2 sm:px-3 py-1 sm:py-2 text-xs sm:text-sm text-muted-foreground hover:text-destructive transition-colors rounded-lg hover:bg-destructive/10"
              >
                <LogOut className="h-4 w-4" />
                <span className="hidden sm:inline">Logout</span>
              </button>
            </div>
          </div>
        </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Desktop Sidebar */}
        <div className="hidden md:block">
          <Sidebar activeSection={activeSection} onSectionChange={setActiveSection} />
        </div>

        <main className="flex-1 overflow-y-auto p-3 sm:p-6 pb-20 md:pb-6">
          {activeSection === "signals" && <TelegramSignals signals={signals} />}
          {activeSection === "positions" && (
            <PositionManagement positions={positions} onClosePosition={handleClosePosition} />
          )}
          {activeSection === "trade-management" && <TradeManagement />}
          {activeSection === "trading" && <ManualTrading onExecuteTrade={handleAddPosition} />}
          {activeSection === "history" && <TradeHistoryComponent history={tradeHistory} />}
        </main>
      </div>

      {/* Mobile Bottom Navigation */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 border-t border-border bg-card">
        <div className="flex items-center justify-around">
          {[
            { id: "signals", label: "Signals", Icon: Radio },
            { id: "positions", label: "Positions", Icon: TrendingUp },
            { id: "trading", label: "Trading", Icon: ArrowLeftRight },
            { id: "history", label: "History", Icon: History },
          ].map((item) => (
            <button
              key={item.id}
              onClick={() => setActiveSection(item.id)}
              className={`flex-1 flex flex-col items-center justify-center gap-1 py-3 px-2 text-xs font-medium transition-colors border-t-2 ${
                activeSection === item.id
                  ? "border-t-primary text-primary"
                  : "border-t-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              <item.Icon className="h-5 w-5" />
              <span className="hidden xs:inline">{item.label}</span>
            </button>
          ))}
        </div>
      </nav>
    </div>
    </ProtectedRoute>
  )
}
