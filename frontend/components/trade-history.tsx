"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import type { TradeHistory } from "@/types/trading"
import { TrendingUp, TrendingDown, Search, X } from "lucide-react"
import { cn } from "@/lib/utils"

interface TradeHistoryProps {
  history: TradeHistory[]
}

export function TradeHistoryComponent({ history }: TradeHistoryProps) {
  const [pairFilter, setPairFilter] = useState("")
  const [dateFrom, setDateFrom] = useState("")
  const [dateTo, setDateTo] = useState("")
  const [pnlFilter, setPnlFilter] = useState("")
  const [pnlComparison, setPnlComparison] = useState<"above" | "below">("above")

  const filteredHistory = history.filter((trade) => {
    // Filter by pair
    if (pairFilter && !trade.pair.toLowerCase().includes(pairFilter.toLowerCase())) {
      return false
    }

    // Filter by date range
    if (dateFrom) {
      const fromDate = new Date(dateFrom)
      if (trade.closedAt < fromDate) return false
    }
    if (dateTo) {
      const toDate = new Date(dateTo)
      toDate.setHours(23, 59, 59, 999) // Include the entire day
      if (trade.closedAt > toDate) return false
    }

    // Filter by PnL percentage
    if (pnlFilter) {
      const pnlValue = Number.parseFloat(pnlFilter)
      if (!isNaN(pnlValue)) {
        if (pnlComparison === "above" && trade.pnlPercentage < pnlValue) return false
        if (pnlComparison === "below" && trade.pnlPercentage > pnlValue) return false
      }
    }

    return true
  })

  const clearFilters = () => {
    setPairFilter("")
    setDateFrom("")
    setDateTo("")
    setPnlFilter("")
    setPnlComparison("above")
  }

  const hasActiveFilters = pairFilter || dateFrom || dateTo || pnlFilter

  const totalTrades = filteredHistory.length
  const totalPnl = filteredHistory.reduce((sum, trade) => sum + trade.pnl, 0)
  const totalValue = filteredHistory.reduce((sum, trade) => sum + trade.totalValue, 0)
  const averagePnlPercentage = totalValue > 0 ? (totalPnl / totalValue) * 100 : 0

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Trade History</h2>
        <p className="text-sm text-muted-foreground">View your completed trades and performance</p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-lg">Filters</CardTitle>
            {hasActiveFilters && (
              <Button variant="ghost" size="sm" onClick={clearFilters} className="h-8 px-2">
                <X className="h-4 w-4 mr-1" />
                Clear
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Currency Pair Filter */}
            <div className="space-y-2">
              <Label htmlFor="pair-filter">Currency Pair</Label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="pair-filter"
                  placeholder="e.g. BTC/USDT"
                  value={pairFilter}
                  onChange={(e) => setPairFilter(e.target.value)}
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
                onChange={(e) => setDateFrom(e.target.value)}
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
                onChange={(e) => setDateTo(e.target.value)}
                className="font-mono"
              />
            </div>

            {/* PnL Percentage Filter */}
            <div className="space-y-2">
              <Label htmlFor="pnl-filter">P&L %</Label>
              <div className="flex gap-2">
                <Select value={pnlComparison} onValueChange={(value: "above" | "below") => setPnlComparison(value)}>
                  <SelectTrigger className="w-24">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="above">Above</SelectItem>
                    <SelectItem value="below">Below</SelectItem>
                  </SelectContent>
                </Select>
                <Input
                  id="pnl-filter"
                  type="number"
                  placeholder="0.00"
                  value={pnlFilter}
                  onChange={(e) => setPnlFilter(e.target.value)}
                  className="font-mono"
                  step="0.01"
                />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {hasActiveFilters && (
        <div className="text-sm text-muted-foreground">
          Showing {filteredHistory.length} of {history.length} trades
        </div>
      )}

      {filteredHistory.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Summary</CardTitle>
            <CardDescription>Statistics for {hasActiveFilters ? "filtered" : "all"} trades</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Total Trades</p>
                <p className="text-2xl font-bold text-foreground">{totalTrades}</p>
              </div>
              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Total P&L</p>
                <p className={cn("text-2xl font-bold font-mono", totalPnl >= 0 ? "text-success" : "text-destructive")}>
                  {totalPnl >= 0 ? "+" : ""}$
                  {totalPnl.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </p>
              </div>
              <div className="space-y-1">
                <p className="text-sm text-muted-foreground">Average P&L %</p>
                <p
                  className={cn(
                    "text-2xl font-bold font-mono",
                    averagePnlPercentage >= 0 ? "text-success" : "text-destructive",
                  )}
                >
                  {averagePnlPercentage >= 0 ? "+" : ""}
                  {averagePnlPercentage.toFixed(2)}%
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4">
        {filteredHistory.length === 0 ? (
          <Card>
            <CardContent className="flex items-center justify-center py-12">
              <p className="text-muted-foreground">
                {hasActiveFilters ? "No trades match your filters" : "No trade history yet"}
              </p>
            </CardContent>
          </Card>
        ) : (
          filteredHistory.map((trade) => (
            <Card key={trade.id}>
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div
                      className={cn("p-2 rounded-lg", trade.side === "LONG" ? "bg-success/10" : "bg-destructive/10")}
                    >
                      {trade.side === "LONG" ? (
                        <TrendingUp className="h-5 w-5 text-success" />
                      ) : (
                        <TrendingDown className="h-5 w-5 text-destructive" />
                      )}
                    </div>
                    <div>
                      <CardTitle className="text-lg">{trade.pair}</CardTitle>
                      <CardDescription>
                        {trade.closedAt.toLocaleDateString()} {trade.closedAt.toLocaleTimeString()}
                      </CardDescription>
                    </div>
                  </div>
                  <Badge variant={trade.side === "LONG" ? "default" : "destructive"}>{trade.side}</Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">Entry Price</p>
                    <p className="font-mono font-semibold">${trade.entryPrice.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">Exit Price</p>
                    <p className="font-mono font-semibold">${trade.exitPrice.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">Total Value</p>
                    <p className="font-mono font-semibold">${trade.totalValue.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">P&L</p>
                    <div className="flex items-center gap-2">
                      <p
                        className={cn("font-mono font-semibold", trade.pnl >= 0 ? "text-success" : "text-destructive")}
                      >
                        {trade.pnl >= 0 ? "+" : ""}${trade.pnl.toLocaleString()}
                      </p>
                      <Badge
                        variant={trade.pnl >= 0 ? "default" : "destructive"}
                        className={cn(
                          "font-mono text-xs",
                          trade.pnl >= 0 ? "bg-success/10 text-success hover:bg-success/20" : "",
                        )}
                      >
                        {trade.pnl >= 0 ? "+" : ""}
                        {trade.pnlPercentage.toFixed(2)}%
                      </Badge>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  )
}
