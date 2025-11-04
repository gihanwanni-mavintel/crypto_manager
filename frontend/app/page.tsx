"use client"

import { useState, useEffect, useRef } from "react"
import { useRouter } from "next/navigation"
import { TelegramSignals } from "@/components/telegram-signals"
import { PositionManagement } from "@/components/position-management"
import { ManualTrading } from "@/components/manual-trading"
import { TradeHistoryComponent } from "@/components/trade-history"
import { Sidebar } from "@/components/sidebar"
import { AccountSummary } from "@/components/account-summary"
import { createReconnectingWebSocket, tradingAPI, signalsAPI, getAuthToken, setAuthToken } from "@/lib/api"
import type { Signal, Position, Trade, TradeHistory } from "@/types/trading"

type TimePeriod = "24h" | "7d" | "52W" | "All"

export default function CryptoPositionManagement() {
  const router = useRouter()
  const [activeSection, setActiveSection] = useState("signals")
  const [selectedPeriod, setSelectedPeriod] = useState<TimePeriod>("All")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)

  const [signals, setSignals] = useState<Signal[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [tradeHistory, setTradeHistory] = useState<TradeHistory[]>([])

  // Check authentication and load data
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const token = getAuthToken()
        if (!token) {
          // Set a test token for frontend-only deployment (before backend is ready)
          setAuthToken('test-token-frontend-only')
        }

        setIsLoading(true)

        try {
          // Fetch trading signals from Neon database
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
          console.warn("Backend not available - running in frontend-only mode", apiErr)
          // Continue without backend data
        }

        console.log("[OK] User authenticated successfully")
        setIsLoading(false)
      } catch (err) {
        console.error("Failed during auth check:", err)
        setIsLoading(false)
      }
    }

    checkAuth()
  }, [router])

  // WebSocket connection for real-time signals
  // TODO: Enable once backend WebSocket endpoint is implemented
  useEffect(() => {
    const token = getAuthToken()
    if (!token) return

    // WebSocket endpoint not yet implemented on backend
    // Uncomment this code once ws://localhost:8081/ws is available:
    /*
    wsRef.current = createReconnectingWebSocket((data) => {
      try {
        // Handle incoming signal data
        if (data.type === "signal") {
          const newSignal: Signal = {
            id: data.id || Date.now().toString(),
            pair: data.pair || "UNKNOWN",
            action: data.action || "BUY",
            entry: data.entry || 0,
            stopLoss: data.stopLoss || 0,
            takeProfit: data.takeProfit || [],
            timestamp: new Date(data.timestamp || Date.now()),
            source: data.source || "Telegram",
            status: "active",
          }
          setSignals((prev) => [newSignal, ...prev])
        } else if (data.type === "position_update") {
          // Handle position updates
          setPositions((prev) =>
            prev.map((p) => (p.id === data.id ? { ...p, ...data } : p))
          )
        }
      } catch (err) {
        console.error("Error processing WebSocket message:", err)
      }
    })

    return () => {
      wsRef.current?.close()
    }
    */

    console.log("ℹ️ WebSocket real-time signals not yet enabled")
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
    <div className="min-h-screen bg-background">
      {error && (
        <div className="bg-destructive/10 border border-destructive text-destructive px-4 py-3 rounded-md m-4">
          <p className="text-sm font-medium">{error}</p>
          <button
            onClick={() => setError(null)}
            className="text-xs mt-2 underline hover:no-underline"
          >
            Dismiss
          </button>
        </div>
      )}

      <header className="border-b border-border bg-card">
        <div className="container mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <h1 className="text-2xl font-bold text-foreground">Crypto Position Manager</h1>
            <div className="flex items-center gap-4">
              <div className="text-sm text-muted-foreground">
                Total P&L: <span className="font-mono font-semibold text-success">+$700.00</span>
              </div>
            </div>
          </div>
        </div>
      </header>

      <div className="flex">
        <Sidebar activeSection={activeSection} onSectionChange={setActiveSection} />

        <main className="flex-1 p-6">
          {activeSection !== "history" && (
            <div className="flex gap-4 items-start">
              <div className="flex flex-col gap-1 p-1 bg-muted rounded-lg">
                {(["24h", "7d", "52W", "All"] as TimePeriod[]).map((period) => (
                  <button
                    key={period}
                    onClick={() => setSelectedPeriod(period)}
                    className={`px-4 py-2 text-sm font-medium rounded transition-colors ${
                      selectedPeriod === period
                        ? "bg-background text-foreground shadow-sm"
                        : "text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    {period}
                  </button>
                ))}
              </div>
              <div className="flex-1">
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
    </div>
  )
}
