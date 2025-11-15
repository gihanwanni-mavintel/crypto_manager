'use client'

import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import type { Signal } from "@/types/trading"
import { TrendingUp, TrendingDown, Clock, TrendingUpIcon } from "lucide-react"

interface ViewSignalDialogProps {
  signal: Signal | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ViewSignalDialog({ signal, open, onOpenChange }: ViewSignalDialogProps) {
  if (!signal) return null

  const isLong = signal.action === "BUY"
  const pnlColor = signal.unrealizedPnL && signal.unrealizedPnL >= 0 ? "text-success" : "text-destructive"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              {signal.pair}
              <Badge variant={isLong ? "default" : "destructive"} className="text-xs">
                {isLong ? <TrendingUp className="h-3 w-3 mr-1" /> : <TrendingDown className="h-3 w-3 mr-1" />}
                {signal.action}
              </Badge>
            </span>
            <Badge variant="outline" className="text-xs">
              {signal.status.toUpperCase()}
            </Badge>
          </DialogTitle>
          <DialogDescription>
            Signal from {signal.source} • {signal.timestamp.toLocaleString()}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Basic Info */}
          <Card className="p-4">
            <h3 className="font-semibold text-sm mb-3">Trading Details</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground text-xs">Entry Price</p>
                <p className="font-mono font-semibold text-base">${signal.entry.toLocaleString()}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-xs">Stop Loss</p>
                <p className="font-mono font-semibold text-base text-destructive">${signal.stopLoss.toLocaleString()}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-xs">Leverage</p>
                <p className="font-mono font-semibold text-base">{signal.leverage}x</p>
              </div>
              {signal.entryQuantity && (
                <div>
                  <p className="text-muted-foreground text-xs">Entry Quantity</p>
                  <p className="font-mono font-semibold text-base">{signal.entryQuantity}</p>
                </div>
              )}
              {signal.account && (
                <div>
                  <p className="text-muted-foreground text-xs">Account</p>
                  <p className="font-semibold text-base">{signal.account}</p>
                </div>
              )}
            </div>
          </Card>

          {/* Entry Order */}
          {signal.entryOrderId && (
            <Card className="p-4">
              <h3 className="font-semibold text-sm mb-3">Entry Order</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Order ID:</span>
                  <code className="bg-muted px-2 py-1 rounded text-xs font-mono">{signal.entryOrderId}</code>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Status:</span>
                  <Badge
                    variant={
                      signal.entryOrderStatus === "FILLED"
                        ? "default"
                        : signal.entryOrderStatus === "PENDING"
                          ? "secondary"
                          : "destructive"
                    }
                    className="text-xs"
                  >
                    {signal.entryOrderStatus}
                  </Badge>
                </div>
              </div>
            </Card>
          )}

          {/* Stop Loss Order */}
          {signal.stopLossOrder && (
            <Card className="p-4">
              <h3 className="font-semibold text-sm mb-3">Stop Loss Order</h3>
              <div className="space-y-2 text-sm">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-muted-foreground text-xs">Price</p>
                    <p className="font-mono font-semibold">${signal.stopLossOrder.price.toLocaleString()}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-xs">Quantity</p>
                    <p className="font-mono font-semibold">{signal.stopLossOrder.quantity}</p>
                  </div>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Order ID:</span>
                  <code className="bg-muted px-2 py-1 rounded text-xs font-mono">{signal.stopLossOrder.orderId}</code>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Status:</span>
                  <Badge
                    variant={
                      signal.stopLossOrder.status === "FILLED"
                        ? "destructive"
                        : signal.stopLossOrder.status === "PENDING"
                          ? "secondary"
                          : "outline"
                    }
                    className="text-xs"
                  >
                    {signal.stopLossOrder.status}
                  </Badge>
                </div>
              </div>
            </Card>
          )}

          {/* Take Profit Orders */}
          {signal.takeProfitOrders && signal.takeProfitOrders.length > 0 && (
            <Card className="p-4">
              <h3 className="font-semibold text-sm mb-3">Take Profit Orders</h3>
              <div className="space-y-3">
                {signal.takeProfitOrders.map((tp, idx) => (
                  <div
                    key={idx}
                    className="border border-border rounded-lg p-3 space-y-2 text-sm"
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-semibold text-muted-foreground">TP{tp.targetLevel}</span>
                      <Badge variant="outline" className="text-xs">
                        ${tp.price.toLocaleString()}
                      </Badge>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <p className="text-muted-foreground text-xs">Quantity</p>
                        <p className="font-mono font-semibold">{tp.quantity}</p>
                      </div>
                      <div>
                        <p className="text-muted-foreground text-xs">Status</p>
                        <Badge
                          variant={
                            tp.status === "FILLED"
                              ? "default"
                              : tp.status === "PENDING"
                                ? "secondary"
                                : "outline"
                          }
                          className="text-xs"
                        >
                          {tp.status}
                        </Badge>
                      </div>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground text-xs">Order ID:</span>
                      <code className="bg-muted px-2 py-1 rounded text-xs font-mono">{tp.orderId}</code>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* P&L Summary */}
          {signal.unrealizedPnL !== undefined && signal.unrealizedPnLPercent !== undefined && (
            <Card className="p-4">
              <h3 className="font-semibold text-sm mb-3">P&L Summary</h3>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-muted-foreground text-xs">Unrealized P&L</p>
                  <p className={`font-mono font-semibold text-base ${pnlColor}`}>
                    {signal.unrealizedPnL >= 0 ? "+" : ""}{signal.unrealizedPnL.toFixed(2)}
                  </p>
                </div>
                <div>
                  <p className="text-muted-foreground text-xs">Percentage</p>
                  <p className={`font-mono font-semibold text-base ${pnlColor}`}>
                    {signal.unrealizedPnLPercent >= 0 ? "+" : ""}{signal.unrealizedPnLPercent.toFixed(2)}%
                  </p>
                </div>
              </div>
            </Card>
          )}

          {/* Footer timestamp */}
          <div className="flex items-center gap-1 text-xs text-muted-foreground pt-2 border-t border-border">
            <Clock className="h-3 w-3" />
            {signal.timestamp.toLocaleString()}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
