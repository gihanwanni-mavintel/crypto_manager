"use client"

import { Card, CardContent } from "@/components/ui/card"
import { Wallet, TrendingUp, DollarSign, PieChart } from "lucide-react"
import type { Position } from "@/types/trading"

interface AccountSummaryProps {
  positions: Position[]
  availableBalance?: number
  selectedPeriod: "24h" | "7d" | "1M" | "52W" | "All"
}

export function AccountSummary({ positions, availableBalance = 50000, selectedPeriod }: AccountSummaryProps) {
  const getFilteredPositions = () => {
    if (selectedPeriod === "All") return positions

    const now = new Date()
    const cutoffDate = new Date()

    switch (selectedPeriod) {
      case "24h":
        cutoffDate.setHours(now.getHours() - 24)
        break
      case "7d":
        cutoffDate.setDate(now.getDate() - 7)
        break
      case "1M":
        cutoffDate.setDate(now.getDate() - 30)
        break
      case "52W":
        cutoffDate.setDate(now.getDate() - 364)
        break
    }

    return positions.filter((pos) => pos.openedAt >= cutoffDate)
  }

  const filteredPositions = getFilteredPositions()

  const totalPositionValue = filteredPositions.reduce(
    (sum, pos) => sum + pos.currentPrice * pos.quantity * pos.leverage,
    0,
  )

  const totalMarginEmployed = filteredPositions.reduce((sum, pos) => sum + pos.entryPrice * pos.quantity, 0)

  const totalPnl = filteredPositions.reduce((sum, pos) => sum + pos.pnl, 0)
  const totalPnlPercentage = totalMarginEmployed > 0 ? (totalPnl / totalMarginEmployed) * 100 : 0

  const stats = [
    {
      title: "Available Balance",
      value: `$${availableBalance.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      icon: Wallet,
      color: "text-blue-500",
      bgColor: "bg-blue-500/10",
    },
    {
      title: "Open Position Value",
      value: `$${totalPositionValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      icon: TrendingUp,
      color: "text-purple-500",
      bgColor: "bg-purple-500/10",
    },
    {
      title: "Total Margin Employed",
      value: `$${totalMarginEmployed.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      icon: PieChart,
      color: "text-orange-500",
      bgColor: "bg-orange-500/10",
    },
  ]

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      {stats.map((stat, index) => {
        const Icon = stat.icon
        return (
          <Card key={index} className="border-border">
            <CardContent className="p-6">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <p className="text-sm text-muted-foreground mb-2">{stat.title}</p>
                  <p className={`text-2xl font-bold font-mono ${stat.color}`}>{stat.value}</p>
                </div>
                <div className={`p-3 rounded-lg ${stat.bgColor}`}>
                  <Icon className={`h-6 w-6 ${stat.color}`} />
                </div>
              </div>
            </CardContent>
          </Card>
        )
      })}

      <Card className="border-border">
        <CardContent className="p-6">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <p className="text-sm text-muted-foreground mb-2">Total P&L</p>
              <p className={`text-2xl font-bold font-mono ${totalPnl >= 0 ? "text-success" : "text-destructive"}`}>
                {totalPnl >= 0 ? "+" : ""}$
                {Math.abs(totalPnl).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </p>
              <p className={`text-sm font-mono mt-1 ${totalPnl >= 0 ? "text-success" : "text-destructive"}`}>
                {totalPnl >= 0 ? "+" : ""}
                {totalPnlPercentage.toFixed(2)}%
              </p>
            </div>
            <div className={`p-3 rounded-lg ${totalPnl >= 0 ? "bg-success/10" : "bg-destructive/10"}`}>
              <DollarSign className={`h-6 w-6 ${totalPnl >= 0 ? "text-success" : "text-destructive"}`} />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
