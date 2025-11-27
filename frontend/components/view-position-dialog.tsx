"use client"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import type { Position } from "@/types/trading"
import { TrendingUp, TrendingDown } from "lucide-react"

interface ViewPositionDialogProps {
  position: Position | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ViewPositionDialog({ position, open, onOpenChange }: ViewPositionDialogProps) {
  if (!position) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {position.pair}
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
          </DialogTitle>
          <DialogDescription>
            Opened {position.openedAt.toLocaleDateString()} at {position.openedAt.toLocaleTimeString()}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* Entry Details */}
          <div className="border rounded-lg p-4 space-y-3">
            <h3 className="font-semibold text-base">Entry Details</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-muted-foreground text-sm">Entry Price</p>
                <p className="font-mono font-bold text-lg">${position.entryPrice.toLocaleString()}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">Current Price</p>
                <p className="font-mono font-bold text-lg">${position.currentPrice.toLocaleString()}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">Quantity</p>
                <p className="font-mono font-bold text-lg">{position.quantity}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">Leverage</p>
                <p className="font-mono font-bold text-lg">{position.leverage}x</p>
              </div>
            </div>
          </div>

          {/* P&L Details */}
          <div className="border rounded-lg p-4 space-y-3">
            <h3 className="font-semibold text-base">Profit & Loss</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-muted-foreground text-sm">P&L (USD)</p>
                <p className={`font-mono font-bold text-lg ${position.pnl >= 0 ? "text-success" : "text-destructive"}`}>
                  {position.pnl >= 0 ? "+" : ""}${position.pnl.toFixed(2)}
                </p>
              </div>
              <div>
                <p className="text-muted-foreground text-sm">P&L (%)</p>
                <p className={`font-mono font-bold text-lg ${position.pnl >= 0 ? "text-success" : "text-destructive"}`}>
                  {position.pnlPercentage >= 0 ? "+" : ""}{position.pnlPercentage.toFixed(2)}%
                </p>
              </div>
            </div>
          </div>

          {/* Risk Management */}
          <div className="border rounded-lg p-4 space-y-3">
            <h3 className="font-semibold text-base">Risk Management</h3>
            <div className="space-y-3">
              {position.stopLoss && (
                <div className="border rounded-lg p-3 bg-destructive/5">
                  <p className="text-muted-foreground text-sm">Stop Loss</p>
                  <p className="font-mono font-bold text-lg text-destructive">
                    ${position.stopLoss.toLocaleString()}
                  </p>
                </div>
              )}

              {position.takeProfitLevels && position.takeProfitLevels.length > 0 && (
                <div className="space-y-2">
                  <p className="text-muted-foreground text-sm font-medium">Take Profit Levels</p>
                  {position.takeProfitLevels.map((tp, index) => (
                    <div key={index} className="border rounded-lg p-3 bg-success/5 space-y-1">
                      <div className="flex justify-between">
                        <p className="text-muted-foreground text-xs">Level {index + 1}</p>
                        <p className="text-xs text-muted-foreground">{tp.status}</p>
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <p className="text-muted-foreground text-xs">Price</p>
                          <p className="font-mono font-bold text-success">
                            ${tp.price.toLocaleString()}
                          </p>
                        </div>
                        <div>
                          <p className="text-muted-foreground text-xs">Quantity</p>
                          <p className="font-mono font-bold text-success">
                            {tp.quantity}
                          </p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {position.takeProfit && !position.takeProfitLevels && (
                <div className="border rounded-lg p-3 bg-success/5">
                  <p className="text-muted-foreground text-sm">Take Profit Target</p>
                  <p className="font-mono font-bold text-lg text-success">
                    ${position.takeProfit.toLocaleString()}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Position Summary */}
          <div className="bg-muted p-4 rounded-lg space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Position Type:</span>
              <span className="font-semibold">{position.side === "LONG" ? "Buy Position" : "Short Position"}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Opened:</span>
              <span className="font-semibold">{position.openedAt.toLocaleDateString()}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Market Price:</span>
              <span className="font-semibold">${position.currentPrice.toLocaleString()}</span>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
