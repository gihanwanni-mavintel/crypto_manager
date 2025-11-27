"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { TrendingUp, TrendingDown, Search, X, Loader2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { tradeHistoryAPI } from "@/lib/api"

interface Trade {
  id: number
  pair: string
  side: string
  entryPrice: number
  exitPrice: number
  quantity: number
  leverage: number
  pnl: number
  pnlPercent: number
  exitReason?: string
  openedAt: string
  closedAt: string
  status: string
}

interface TradeHistoryResponse {
  trades: Trade[]
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
  totalTrades: number
  winningTrades: number
  losingTrades: number
  winRate: number
  totalPnL: number
  averagePnL: number
  averagePnLPct: number
  bestTrade?: Trade
  worstTrade?: Trade
  largestWin: number
  largestLoss: number
  profitFactor: number
  winLossRatio: number
  consecutiveWins: number
  consecutiveLosses: number
}

interface TradeHistoryProps {
  history?: Trade[]
}

export function TradeHistoryComponent({ history: initialHistory }: TradeHistoryProps) {
  const [symbol, setSymbol] = useState("")
  const [dateFrom, setDateFrom] = useState("")
  const [dateTo, setDateTo] = useState("")
  const [pnlMinFilter, setPnlMinFilter] = useState("")
  const [pnlMaxFilter, setPnlMaxFilter] = useState("")
  const [sideFilter, setSideFilter] = useState<string | null>(null)
  const [exitReasonFilter, setExitReasonFilter] = useState<string | null>(null)
  const [sortBy, setSortBy] = useState("closedAt")
  const [sortOrder, setSortOrder] = useState<"ASC" | "DESC">("DESC")
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<TradeHistoryResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Fetch trade history on filter/pagination changes
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true)
      setError(null)
      try {
        const filter = {
          symbol: symbol || null,
          fromDate: dateFrom || null,
          toDate: dateTo || null,
          pnlMin: pnlMinFilter ? parseFloat(pnlMinFilter) : null,
          pnlMax: pnlMaxFilter ? parseFloat(pnlMaxFilter) : null,
          pnlPercentMin: null,
          pnlPercentMax: null,
          side: sideFilter,
          status: "CLOSED",
          exitReason: exitReasonFilter,
          sortBy,
          sortOrder,
          page,
          pageSize,
        }
        const response = await tradeHistoryAPI.getClosedTrades(filter)
        setData(response)
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to fetch trade history")
        console.error("Trade history fetch error:", err)
      } finally {
        setLoading(false)
      }
    }

    fetchData()
  }, [symbol, dateFrom, dateTo, pnlMinFilter, pnlMaxFilter, sideFilter, exitReasonFilter, sortBy, sortOrder, page, pageSize])

  const clearFilters = () => {
    setSymbol("")
    setDateFrom("")
    setDateTo("")
    setPnlMinFilter("")
    setPnlMaxFilter("")
    setSideFilter(null)
    setExitReasonFilter(null)
    setPage(0)
  }

  const hasActiveFilters = symbol || dateFrom || dateTo || pnlMinFilter || pnlMaxFilter || sideFilter || exitReasonFilter

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg sm:text-2xl font-bold text-foreground">Trade History</h2>
        <p className="text-xs sm:text-sm text-muted-foreground">View your completed trades and performance</p>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <CardTitle className="text-base sm:text-lg">Filters</CardTitle>
            {hasActiveFilters && (
              <Button variant="ghost" size="sm" onClick={clearFilters} className="h-8 px-2 w-fit">
                <X className="h-4 w-4 mr-1" />
                Clear
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2 sm:gap-4">
            {/* Symbol Filter */}
            <div className="space-y-2">
              <Label htmlFor="symbol-filter">Symbol</Label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="symbol-filter"
                  placeholder="e.g. BTCUSDT"
                  value={symbol}
                  onChange={(e) => { setSymbol(e.target.value); setPage(0); }}
                  className="pl-9"
                />
              </div>
            </div>

            {/* Date From Filter */}
            <div className="space-y-2">
              <Label htmlFor="date-from">From Date</Label>
              <Input
                id="date-from"
                type="date"
                value={dateFrom}
                onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
                className="font-mono"
              />
            </div>

            {/* Date To Filter */}
            <div className="space-y-2">
              <Label htmlFor="date-to">To Date</Label>
              <Input
                id="date-to"
                type="date"
                value={dateTo}
                onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
                className="font-mono"
              />
            </div>

            {/* Side Filter */}
            <div className="space-y-2">
              <Label htmlFor="side-filter">Side</Label>
              <Select value={sideFilter || "all"} onValueChange={(value) => { setSideFilter(value === "all" ? null : value); setPage(0); }}>
                <SelectTrigger className="text-xs sm:text-sm">
                  <SelectValue placeholder="All sides" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All sides</SelectItem>
                  <SelectItem value="BUY">BUY</SelectItem>
                  <SelectItem value="SELL">SELL</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Exit Reason Filter */}
            <div className="space-y-2">
              <Label htmlFor="exit-reason-filter">Exit Reason</Label>
              <Select value={exitReasonFilter || "all"} onValueChange={(value) => { setExitReasonFilter(value === "all" ? null : value); setPage(0); }}>
                <SelectTrigger className="text-xs sm:text-sm">
                  <SelectValue placeholder="All reasons" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All reasons</SelectItem>
                  <SelectItem value="TP1">TP1</SelectItem>
                  <SelectItem value="TP2">TP2</SelectItem>
                  <SelectItem value="TP3">TP3</SelectItem>
                  <SelectItem value="TP4">TP4</SelectItem>
                  <SelectItem value="SL">Stop Loss</SelectItem>
                  <SelectItem value="MANUAL">Manual</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* P&L Range Filters */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 sm:gap-4 mt-4">
            <div className="space-y-2">
              <Label htmlFor="pnl-min">Min P&L ($)</Label>
              <Input
                id="pnl-min"
                type="number"
                placeholder="0.00"
                value={pnlMinFilter}
                onChange={(e) => { setPnlMinFilter(e.target.value); setPage(0); }}
                className="font-mono"
                step="0.01"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="pnl-max">Max P&L ($)</Label>
              <Input
                id="pnl-max"
                type="number"
                placeholder="0.00"
                value={pnlMaxFilter}
                onChange={(e) => { setPnlMaxFilter(e.target.value); setPage(0); }}
                className="font-mono"
                step="0.01"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Loading State */}
      {loading && (
        <Card>
          <CardContent className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <p className="ml-2 text-muted-foreground">Loading trades...</p>
          </CardContent>
        </Card>
      )}

      {/* Error State */}
      {error && (
        <Card className="border-destructive">
          <CardContent className="flex items-center justify-center py-12">
            <p className="text-destructive">{error}</p>
          </CardContent>
        </Card>
      )}

      {/* Results Count */}
      {data && !loading && (
        <div className="text-sm text-muted-foreground">
          Showing {data.trades.length} of {data.totalCount} trades (Page {data.page + 1} of {data.totalPages})
        </div>
      )}

      {/* Statistics Summary */}
      {data && data.trades.length > 0 && !loading && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base sm:text-lg">Summary Statistics</CardTitle>
            <CardDescription className="text-xs sm:text-sm">Performance metrics for {hasActiveFilters ? "filtered" : "all"} trades</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
              {/* Total Trades */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Total Trades</p>
                <p className="text-lg sm:text-2xl font-bold text-foreground">{data.totalTrades}</p>
              </div>

              {/* Win Rate */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Win Rate</p>
                <p className="text-lg sm:text-2xl font-bold text-foreground">
                  {data.winRate.toFixed(1)}%
                </p>
                <p className="text-xs text-muted-foreground">
                  {data.winningTrades}W / {data.losingTrades}L
                </p>
              </div>

              {/* Total P&L */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Total P&L</p>
                <p className={cn("text-lg sm:text-2xl font-bold font-mono", data.totalPnL >= 0 ? "text-success" : "text-destructive")}>
                  {data.totalPnL >= 0 ? "+" : ""}${data.totalPnL.toFixed(2)}
                </p>
              </div>

              {/* Average P&L % */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Average P&L %</p>
                <p className={cn("text-lg sm:text-2xl font-bold font-mono", data.averagePnLPct >= 0 ? "text-success" : "text-destructive")}>
                  {data.averagePnLPct >= 0 ? "+" : ""}
                  {data.averagePnLPct.toFixed(2)}%
                </p>
              </div>

              {/* Profit Factor */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Profit Factor</p>
                <p className="text-lg sm:text-2xl font-bold text-foreground">{data.profitFactor.toFixed(2)}</p>
              </div>

              {/* Win/Loss Ratio */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Win/Loss Ratio</p>
                <p className="text-lg sm:text-2xl font-bold text-foreground">{data.winLossRatio.toFixed(2)}</p>
              </div>

              {/* Largest Win */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Largest Win</p>
                <p className="text-lg sm:text-2xl font-bold text-success">${data.largestWin.toFixed(2)}</p>
              </div>

              {/* Largest Loss */}
              <div className="space-y-1">
                <p className="text-xs sm:text-sm text-muted-foreground">Largest Loss</p>
                <p className="text-lg sm:text-2xl font-bold text-destructive">${data.largestLoss.toFixed(2)}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Trades List */}
      <div className="grid gap-4">
        {!loading && data && data.trades.length === 0 ? (
          <Card>
            <CardContent className="flex items-center justify-center py-12">
              <p className="text-muted-foreground">
                {hasActiveFilters ? "No trades match your filters" : "No trade history yet"}
              </p>
            </CardContent>
          </Card>
        ) : (
          data?.trades.map((trade) => (
            <Card key={trade.id}>
              <CardHeader className="pb-2 sm:pb-3">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-3">
                  <div className="flex items-center gap-2 sm:gap-3 min-w-0">
                    <div
                      className={cn("p-2 rounded-lg flex-shrink-0", trade.side === "BUY" ? "bg-success/10" : "bg-destructive/10")}
                    >
                      {trade.side === "BUY" ? (
                        <TrendingUp className="h-4 w-4 sm:h-5 sm:w-5 text-success" />
                      ) : (
                        <TrendingDown className="h-4 w-4 sm:h-5 sm:w-5 text-destructive" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <CardTitle className="text-base sm:text-lg truncate">{trade.pair}</CardTitle>
                      <CardDescription className="text-xs truncate">
                        {new Date(trade.closedAt).toLocaleDateString()} {new Date(trade.closedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </CardDescription>
                    </div>
                  </div>
                  <div className="flex gap-1 sm:gap-2 flex-shrink-0">
                    <Badge variant={trade.side === "BUY" ? "default" : "destructive"} className="text-xs w-fit">{trade.side}</Badge>
                    {trade.exitReason && (
                      <Badge variant="outline" className="text-xs w-fit">{trade.exitReason}</Badge>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 lg:grid-cols-5 gap-2 sm:gap-4 text-xs sm:text-sm">
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">Entry Price</p>
                    <p className="font-mono font-semibold truncate">${trade.entryPrice.toLocaleString()}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">Exit Price</p>
                    <p className="font-mono font-semibold truncate">${trade.exitPrice.toLocaleString()}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">Quantity</p>
                    <p className="font-mono font-semibold truncate">{trade.quantity}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">P&L</p>
                    <div className="flex items-center gap-1 sm:gap-2">
                      <p
                        className={cn("font-mono font-semibold truncate", trade.pnl >= 0 ? "text-success" : "text-destructive")}
                      >
                        {trade.pnl >= 0 ? "+" : ""}${trade.pnl.toFixed(2)}
                      </p>
                      <Badge
                        variant={trade.pnl >= 0 ? "default" : "destructive"}
                        className={cn(
                          "font-mono text-xs flex-shrink-0",
                          trade.pnl >= 0 ? "bg-success/10 text-success hover:bg-success/20" : "",
                        )}
                      >
                        {trade.pnl >= 0 ? "+" : ""}
                        {trade.pnlPercent.toFixed(2)}%
                      </Badge>
                    </div>
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">Leverage</p>
                    <p className="font-mono font-semibold truncate">{trade.leverage}x</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && !loading && (
        <Card>
          <CardContent className="flex items-center justify-between py-4">
            <div className="text-sm text-muted-foreground">
              Page {data.page + 1} of {data.totalPages}
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                disabled={page === data.totalPages - 1}
              >
                Next
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
