"use client"

import { useState, useEffect, useRef } from "react"
import { TelegramSignals } from "@/components/telegram-signals"
import { PositionManagement } from "@/components/position-management"
import { ManualTrading } from "@/components/manual-trading"
import { TradeHistoryComponent } from "@/components/trade-history"
import { Sidebar } from "@/components/sidebar"
import { AccountSummary } from "@/components/account-summary"
import { tradingAPI, signalsAPI, createWebSocketConnection } from "@/lib/api"
import { Radio, TrendingUp, ArrowLeftRight, History } from "lucide-react"
import type { Signal, Position, Trade, TradeHistory } from "@/types/trading"

type TimePeriod = "24h" | "7d" | "52W" | "All"

export default function CryptoPositionManagement() {
  const [activeSection, setActiveSection] = useState("signals")
  const [selectedPeriod, setSelectedPeriod] = useState<TimePeriod>("All")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [signals, setSignals] = useState<Signal[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [tradeHistory, setTradeHistory] = useState<TradeHistory[]>([])
  const wsRef = useRef<WebSocket | null>(null)

  // Load initial data
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
          takeProfit: [signal.tp1, signal.tp2, signal.tp3, signal.tp4].filter(tp => tp != null),
          timestamp: new Date(signal.timestamp),
          source: signal.channel || "Telegram",
          status: "active",
        }))

        setSignals(transformedSignals)
      } catch (apiErr) {
        console.log("Backend not available - running in frontend-only mode")
      }

      setIsLoading(false)
    }

    loadData()
  }, [])

  // WebSocket connection for real-time signals
  useEffect(() => {
    const connectWebSocket = () => {
      try {
        wsRef.current = createWebSocketConnection(
          (data) => {
            // Handle incoming signal from WebSocket
            console.log("[WebSocket] Received signal:", data)

            if (data && data.pair) {
              const newSignal: Signal = {
                id: data.id ? data.id.toString() : Date.now().toString(),
                pair: data.pair || "UNKNOWN",
                action: data.setupType === "LONG" ? "BUY" : data.setupType === "SHORT" ? "SELL" : "BUY",
                entry: parseFloat(data.entry) || 0,
                stopLoss: parseFloat(data.stopLoss) || 0,
                takeProfit: [data.tp1, data.tp2, data.tp3, data.tp4]
                  .filter(tp => tp != null && tp !== undefined)
                  .map(tp => parseFloat(tp)),
                timestamp: new Date(data.timestamp || Date.now()),
                source: data.channel || "Telegram",
                status: "active",
              }

              // Add new signal to the beginning of the list
              setSignals((prevSignals) => {
                // Avoid duplicates by checking if signal already exists
                const isDuplicate = prevSignals.some(s => s.id === newSignal.id)
                if (isDuplicate) return prevSignals
                return [newSignal, ...prevSignals]
              })
            }
          },
          (error) => {
            console.error("[WebSocket] Connection error:", error)
          },
          () => {
            console.log("[WebSocket] Connection closed, will attempt to reconnect...")
          }
        )
      } catch (err) {
        console.error("[WebSocket] Failed to connect:", err)
      }
    }

    connectWebSocket()

    // Cleanup on unmount
    return () => {
      if (wsRef.current) {
        wsRef.current.close()
        wsRef.current = null
      }
    }
  }, [])

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
        <div className="container mx-auto px-3 sm:px-4 py-3 sm:py-4">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-4">
            <h1 className="text-lg sm:text-2xl font-bold text-foreground">Crypto Position Manager</h1>
            <div className="text-xs sm:text-sm text-muted-foreground">
              Total P&L: <span className="font-mono font-semibold text-success">+$700.00</span>
            </div>
          </div>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Desktop Sidebar */}
        <div className="hidden md:block">
          <Sidebar activeSection={activeSection} onSectionChange={setActiveSection} />
        </div>

        <main className="flex-1 overflow-y-auto p-3 sm:p-6 pb-20 md:pb-6">
          {activeSection !== "history" && (
            <div className="flex flex-col sm:flex-row gap-3 sm:gap-4 mb-4 sm:items-start">
              <div className="flex flex-row sm:flex-col gap-1 p-2 bg-muted rounded-lg w-full sm:w-fit sm:flex-shrink-0">
                {(["24h", "7d", "52W", "All"] as TimePeriod[]).map((period) => (
                  <button
                    key={period}
                    onClick={() => setSelectedPeriod(period)}
                    className={`flex-1 sm:flex-none px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium rounded transition-colors ${
                      selectedPeriod === period
                        ? "bg-background text-foreground shadow-sm"
                        : "text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    {period}
                  </button>
                ))}
              </div>
              <div className="w-full">
                <AccountSummary positions={positions} selectedPeriod={selectedPeriod} />
              </div>
            </div>
          )}

          {activeSection === "signals" && <TelegramSignals signals={signals} />}
          {activeSection === "positions" && (
            <PositionManagement positions={positions} onClosePosition={handleClosePosition} />
          )}
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
  )
}
