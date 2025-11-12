'use client'

import { useState, useEffect } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import { useToast } from "@/hooks/use-toast"
import type { Signal } from "@/types/trading"
import { TrendingUp, TrendingDown, AlertCircle } from "lucide-react"

interface EditSignalDialogProps {
  signal: Signal | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSave?: (updatedSignal: Signal) => Promise<void>
}

export function EditSignalDialog({ signal, open, onOpenChange, onSave }: EditSignalDialogProps) {
  const { toast } = useToast()
  const [isLoading, setIsLoading] = useState(false)
  const [editedSignal, setEditedSignal] = useState<Signal | null>(signal)

  // Sync editedSignal when signal prop changes
  useEffect(() => {
    setEditedSignal(signal)
  }, [signal])

  const isLong = editedSignal?.action === "BUY"

  const handleStopLossChange = (field: "price" | "quantity", value: string) => {
    if (!editedSignal) return
    const numValue = parseFloat(value)
    if (isNaN(numValue)) return

    setEditedSignal({
      ...editedSignal,
      stopLoss: field === "price" ? numValue : editedSignal.stopLoss,
      stopLossOrder: editedSignal.stopLossOrder
        ? {
            ...editedSignal.stopLossOrder,
            price: field === "price" ? numValue : editedSignal.stopLossOrder.price,
            quantity: field === "quantity" ? numValue : editedSignal.stopLossOrder.quantity,
          }
        : undefined,
    })
  }

  const handleTakeProfitChange = (idx: number, field: "price" | "quantity", value: string) => {
    if (!editedSignal) return
    const numValue = parseFloat(value)
    if (isNaN(numValue)) return

    const updatedTakeProfit = [...editedSignal.takeProfit]
    updatedTakeProfit[idx] = field === "price" ? numValue : editedSignal.takeProfit[idx]

    const updatedTPOrders = editedSignal.takeProfitOrders
      ? editedSignal.takeProfitOrders.map((tp, tpIdx) =>
          tpIdx === idx
            ? {
                ...tp,
                price: field === "price" ? numValue : tp.price,
                quantity: field === "quantity" ? numValue : tp.quantity,
              }
            : tp
        )
      : []

    setEditedSignal({
      ...editedSignal,
      takeProfit: updatedTakeProfit,
      takeProfitOrders: updatedTPOrders,
    })
  }

  const handleSave = async () => {
    if (!editedSignal) return

    // Validation
    if (editedSignal.stopLoss >= editedSignal.entry && isLong) {
      toast({
        title: "Invalid Stop Loss",
        description: "Stop Loss price must be lower than Entry price for LONG positions",
        variant: "destructive",
      })
      return
    }

    if (editedSignal.takeProfit.some((tp) => tp <= editedSignal.entry) && isLong) {
      toast({
        title: "Invalid Take Profit",
        description: "All Take Profit prices must be higher than Entry price for LONG positions",
        variant: "destructive",
      })
      return
    }

    setIsLoading(true)
    try {
      if (onSave) {
        await onSave(editedSignal)
        toast({
          title: "Success",
          description: "Signal updated successfully",
        })
        onOpenChange(false)
      }
    } catch (error) {
      toast({
        title: "Error",
        description: error instanceof Error ? error.message : "Failed to update signal",
        variant: "destructive",
      })
    } finally {
      setIsLoading(false)
    }
  }

  if (!editedSignal) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              Edit {editedSignal.pair}
              <Badge variant={isLong ? "default" : "destructive"} className="text-xs">
                {isLong ? <TrendingUp className="h-3 w-3 mr-1" /> : <TrendingDown className="h-3 w-3 mr-1" />}
                {editedSignal.action}
              </Badge>
            </span>
          </DialogTitle>
          <DialogDescription>Modify Stop Loss and Take Profit orders according to Binance requirements</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Read-Only Info */}
          <Card className="p-4 bg-muted/50">
            <h3 className="font-semibold text-sm mb-3 flex items-center gap-2">
              <AlertCircle className="h-4 w-4" />
              Read-Only Information
            </h3>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground text-xs">Symbol</p>
                <p className="font-semibold">{editedSignal.pair}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-xs">Entry Price</p>
                <p className="font-mono font-semibold">${editedSignal.entry.toLocaleString()}</p>
              </div>
              <div>
                <p className="text-muted-foreground text-xs">Leverage</p>
                <p className="font-semibold">{editedSignal.leverage}x</p>
              </div>
            </div>
          </Card>

          {/* Stop Loss - Editable */}
          <Card className="p-4 border-orange-200 bg-orange-50/50">
            <h3 className="font-semibold text-sm mb-3 text-destructive">Stop Loss (Editable)</h3>
            <div className="space-y-3 text-sm">
              <div>
                <label className="text-muted-foreground text-xs block mb-1">Price</label>
                <Input
                  type="number"
                  step="0.01"
                  value={editedSignal.stopLoss}
                  onChange={(e) => handleStopLossChange("price", e.target.value)}
                  placeholder="Enter SL price"
                  className="font-mono"
                />
                <p className="text-xs text-muted-foreground mt-1">
                  {isLong ? "Must be below" : "Must be above"} entry price (${editedSignal.entry.toLocaleString()})
                </p>
              </div>
              {editedSignal.stopLossOrder && (
                <div>
                  <label className="text-muted-foreground text-xs block mb-1">Quantity</label>
                  <Input
                    type="number"
                    step="0.01"
                    value={editedSignal.stopLossOrder.quantity}
                    onChange={(e) => handleStopLossChange("quantity", e.target.value)}
                    placeholder="Enter SL quantity"
                    className="font-mono"
                  />
                </div>
              )}
            </div>
          </Card>

          {/* Take Profit - Editable */}
          <Card className="p-4 border-green-200 bg-green-50/50">
            <h3 className="font-semibold text-sm mb-3 text-success">Take Profit Targets (Editable)</h3>
            <div className="space-y-3">
              {editedSignal.takeProfit.map((tp, idx) => {
                const tpOrder = editedSignal.takeProfitOrders?.[idx]
                return (
                  <div key={idx} className="border border-border rounded-lg p-3 space-y-2 text-sm bg-white">
                    <p className="font-semibold text-muted-foreground">TP{idx + 1}</p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="text-muted-foreground text-xs block mb-1">Price</label>
                        <Input
                          type="number"
                          step="0.01"
                          value={tp}
                          onChange={(e) => handleTakeProfitChange(idx, "price", e.target.value)}
                          placeholder="TP price"
                          className="font-mono"
                        />
                        <p className="text-xs text-muted-foreground mt-1">
                          {isLong ? "Must be above" : "Must be below"} entry (${editedSignal.entry.toLocaleString()})
                        </p>
                      </div>
                      {tpOrder && (
                        <div>
                          <label className="text-muted-foreground text-xs block mb-1">Quantity</label>
                          <Input
                            type="number"
                            step="0.01"
                            value={tpOrder.quantity}
                            onChange={(e) => handleTakeProfitChange(idx, "quantity", e.target.value)}
                            placeholder="TP quantity"
                            className="font-mono"
                          />
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </Card>

          {/* Important Notes */}
          <Card className="p-3 bg-blue-50/50 border-blue-200">
            <p className="text-xs text-muted-foreground leading-relaxed">
              <strong>Note:</strong> These changes will modify your orders on Binance. Entry price and leverage cannot be
              changed after opening a position. Only SL and TP orders can be modified.
            </p>
          </Card>
        </div>

        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isLoading}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={isLoading}>
            {isLoading ? "Saving..." : "Save Changes"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
