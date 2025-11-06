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
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground">Active Positions</h2>
          <p className="text-sm text-muted-foreground">Monitor and manage your open positions</p>
        </div>
        <div className="text-right">
          <p className="text-sm text-muted-foreground">Total P&L</p>
          <p className={`text-2xl font-bold font-mono ${totalPnl >= 0 ? "text-success" : "text-destructive"}`}>
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
        <div className="grid gap-4">
          {positions.map((position) => (
            <Card key={position.id}>
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-xl font-bold">{position.pair}</CardTitle>
                    <CardDescription className="text-xs">Opened {position.openedAt.toLocaleString()}</CardDescription>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge
                      variant={position.side === "LONG" ? "default" : "destructive"}
                      className="flex items-center gap-1"
                    >
                      {position.side === "LONG" ? (
                        <TrendingUp className="h-3 w-3" />
                      ) : (
                        <TrendingDown className="h-3 w-3" />
                      )}
                      {position.side}
                    </Badge>
                    <Badge variant="outline">{position.leverage}x</Badge>
                  </div>
                </div>
              </CardHeader>

              <CardContent>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                  <div>
                    <p className="text-muted-foreground text-xs">Entry Price</p>
                    <p className="font-mono font-semibold">${position.entryPrice.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Current Price</p>
                    <p className="font-mono font-semibold">${position.currentPrice.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Quantity</p>
                    <p className="font-mono font-semibold">{position.quantity}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">P&L</p>
                    <div>
                      <p className={`font-mono font-bold ${position.pnl >= 0 ? "text-success" : "text-destructive"}`}>
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
                  <div className="grid grid-cols-2 gap-4 mb-4 p-3 bg-muted rounded-lg">
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
