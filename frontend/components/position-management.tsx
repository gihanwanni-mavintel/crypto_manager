"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import type { Position } from "@/types/trading"
import { TrendingUp, TrendingDown, X } from "lucide-react"

interface PositionManagementProps {
  positions: Position[]
  onClosePosition: (id: string) => void
}

export function PositionManagement({ positions, onClosePosition }: PositionManagementProps) {
  const totalPnl = positions.reduce((sum, pos) => sum + pos.pnl, 0)

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h2 className="text-lg sm:text-2xl font-bold text-foreground">Active Positions</h2>
          <p className="text-xs sm:text-sm text-muted-foreground">Monitor and manage your open positions</p>
        </div>
        <div className="text-left sm:text-right">
          <p className="text-xs sm:text-sm text-muted-foreground">Total P&L</p>
          <p className={`text-lg sm:text-2xl font-bold font-mono ${totalPnl >= 0 ? "text-success" : "text-destructive"}`}>
            {totalPnl >= 0 ? "+" : ""}${totalPnl.toFixed(2)}
          </p>
        </div>
      </div>

      {positions.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <p className="text-muted-foreground">No active positions</p>
            <p className="text-sm text-muted-foreground">Open a position from the Manual Trading tab</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3 sm:gap-4">
          {positions.map((position) => (
            <Card key={position.id}>
              <CardHeader className="pb-2 sm:pb-3">
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2 sm:gap-3">
                  <div className="min-w-0">
                    <CardTitle className="text-base sm:text-lg font-bold truncate">{position.pair}</CardTitle>
                    <CardDescription className="text-xs truncate">Opened {position.openedAt.toLocaleDateString()}</CardDescription>
                  </div>
                  <div className="flex items-center gap-1 sm:gap-2 flex-shrink-0">
                    <Badge
                      variant={position.side === "LONG" ? "default" : "destructive"}
                      className="flex items-center gap-1 text-xs"
                    >
                      {position.side === "LONG" ? (
                        <TrendingUp className="h-3 w-3" />
                      ) : (
                        <TrendingDown className="h-3 w-3" />
                      )}
                      {position.side}
                    </Badge>
                    <Badge variant="outline" className="text-xs">{position.leverage}x</Badge>
                  </div>
                </div>
              </CardHeader>

              <CardContent>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-4 mb-3 sm:mb-4 text-xs sm:text-sm">
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">Entry Price</p>
                    <p className="font-mono font-semibold truncate">${position.entryPrice.toLocaleString()}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">Current Price</p>
                    <p className="font-mono font-semibold truncate">${position.currentPrice.toLocaleString()}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">Quantity</p>
                    <p className="font-mono font-semibold truncate">{position.quantity}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">P&L</p>
                    <div>
                      <p className={`font-mono font-bold truncate ${position.pnl >= 0 ? "text-success" : "text-destructive"}`}>
                        {position.pnl >= 0 ? "+" : ""}${position.pnl.toFixed(2)}
                      </p>
                      <p className={`text-xs font-mono ${position.pnl >= 0 ? "text-success" : "text-destructive"}`}>
                        {position.pnl >= 0 ? "+" : ""}
                        {position.pnlPercentage.toFixed(2)}%
                      </p>
                    </div>
                  </div>
                </div>

                {(position.stopLoss || position.takeProfit) && (
                  <div className="grid grid-cols-2 gap-2 sm:gap-4 mb-3 sm:mb-4 p-2 sm:p-3 bg-muted rounded-lg text-xs sm:text-sm">
                    {position.stopLoss && (
                      <div>
                        <p className="text-muted-foreground text-xs">Stop Loss</p>
                        <p className="font-mono text-sm font-semibold text-destructive">
                          ${position.stopLoss.toLocaleString()}
                        </p>
                      </div>
                    )}
                    {position.takeProfit && (
                      <div>
                        <p className="text-muted-foreground text-xs">Take Profit</p>
                        <p className="font-mono text-sm font-semibold text-success">
                          ${position.takeProfit.toLocaleString()}
                        </p>
                      </div>
                    )}
                  </div>
                )}

                <Button variant="destructive" size="sm" className="w-full" onClick={() => onClosePosition(position.id)}>
                  <X className="h-4 w-4 mr-2" />
                  Close Position
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
