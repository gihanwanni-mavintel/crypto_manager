"use client"

import { useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import type { Position, TakeProfitLevel } from "@/types/trading"
import { TrendingUp, TrendingDown, X, Plus } from "lucide-react"
import { positionsAPI } from "@/lib/api"

interface EditPositionDialogProps {
  position: Position | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSave: (position: Position) => Promise<void>
}

export function EditPositionDialog({ position, open, onOpenChange, onSave }: EditPositionDialogProps) {
  const [stopLoss, setStopLoss] = useState<string>("")
  const [takeProfits, setTakeProfits] = useState<Array<{ price: string; quantity: string }>>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Initialize state when position changes
  if (position && (stopLoss === "" || takeProfits.length === 0)) {
    if (stopLoss === "") {
      setStopLoss(position.stopLoss ? position.stopLoss.toString() : "")
    }
    if (takeProfits.length === 0 && position.takeProfitLevels) {
      setTakeProfits(
        position.takeProfitLevels.map((tp) => ({
          price: tp.price.toString(),
          quantity: tp.quantity.toString(),
        }))
      )
    } else if (takeProfits.length === 0) {
      setTakeProfits([{ price: "", quantity: "" }])
    }
  }

  if (!position) return null

  const handleAddTPLevel = () => {
    setTakeProfits([...takeProfits, { price: "", quantity: "" }])
  }

  const handleRemoveTPLevel = (index: number) => {
    if (takeProfits.length > 1) {
      setTakeProfits(takeProfits.filter((_, i) => i !== index))
    }
  }

  const handleUpdateTPLevel = (index: number, field: "price" | "quantity", value: string) => {
    const updated = [...takeProfits]
    updated[index] = { ...updated[index], [field]: value }
    setTakeProfits(updated)
    setError(null)
  }

  const validateAndSave = async () => {
    setError(null)

    // Validate SL
    if (!stopLoss) {
      setError("Stop Loss price is required")
      return
    }

    const slPrice = parseFloat(stopLoss)
    if (isNaN(slPrice) || slPrice <= 0) {
      setError("Stop Loss must be a valid price greater than 0")
      return
    }

    // Validate position-specific SL
    if (position.side === "LONG") {
      if (slPrice >= position.entryPrice) {
        setError(`Stop Loss ($${slPrice}) must be below entry price ($${position.entryPrice})`)
        return
      }
    } else {
      if (slPrice <= position.entryPrice) {
        setError(`Stop Loss ($${slPrice}) must be above entry price ($${position.entryPrice})`)
        return
      }
    }

    // Validate TPs
    if (takeProfits.length === 0) {
      setError("At least one Take Profit level is required")
      return
    }

    const parsedTPs: Array<{ price: number; quantity: number }> = []

    for (let i = 0; i < takeProfits.length; i++) {
      const tp = takeProfits[i]

      if (!tp.price || !tp.quantity) {
        setError(`Take Profit level ${i + 1}: both price and quantity are required`)
        return
      }

      const tpPrice = parseFloat(tp.price)
      const tpQty = parseFloat(tp.quantity)

      if (isNaN(tpPrice) || tpPrice <= 0) {
        setError(`Take Profit level ${i + 1}: price must be a valid number greater than 0`)
        return
      }

      if (isNaN(tpQty) || tpQty <= 0) {
        setError(`Take Profit level ${i + 1}: quantity must be a valid number greater than 0`)
        return
      }

      if (tpQty > position.quantity) {
        setError(`Take Profit level ${i + 1}: quantity ($${tpQty}) cannot exceed position quantity ($${position.quantity})`)
        return
      }

      // Validate position-specific TP
      if (position.side === "LONG") {
        if (tpPrice <= position.entryPrice) {
          setError(`Take Profit level ${i + 1} ($${tpPrice}) must be above entry price ($${position.entryPrice})`)
          return
        }
      } else {
        if (tpPrice >= position.entryPrice) {
          setError(`Take Profit level ${i + 1} ($${tpPrice}) must be below entry price ($${position.entryPrice})`)
          return
        }
      }

      parsedTPs.push({ price: tpPrice, quantity: tpQty })
    }

    try {
      setIsLoading(true)

      // Update SL via API
      if (position.binanceSymbol && position.side) {
        await positionsAPI.updateStopLoss(
          position.binanceSymbol,
          slPrice,
          position.quantity,
          position.side,
          2
        )
      }

      // Update TPs via API
      if (position.binanceSymbol && position.side) {
        const tpLevelData = parsedTPs.map((tp) => ({
          price: tp.price,
          quantity: tp.quantity,
        }))
        await positionsAPI.updateTakeProfits(position.binanceSymbol, tpLevelData, position.side, 2)
      }

      // Update local state
      const updatedPosition: Position = {
        ...position,
        stopLoss: slPrice,
        takeProfitLevels: parsedTPs.map((tp, i) => ({
          orderId: position.takeProfitLevels?.[i]?.orderId || "",
          price: tp.price,
          quantity: tp.quantity,
          status: position.takeProfitLevels?.[i]?.status || "PENDING",
        })),
      }

      await onSave(updatedPosition)
      onOpenChange(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save changes")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            Edit {position.pair}
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
            Adjust Stop Loss and Take Profit levels for this position. Changes will be applied to your Binance orders.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Position Info */}
          <div className="bg-muted p-3 rounded-lg space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Entry Price:</span>
              <span className="font-mono font-semibold">${position.entryPrice.toLocaleString()}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Current Price:</span>
              <span className="font-mono font-semibold">${position.currentPrice.toLocaleString()}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Leverage:</span>
              <span className="font-mono font-semibold">{position.leverage}x</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Quantity:</span>
              <span className="font-mono font-semibold">{position.quantity}</span>
            </div>
          </div>

          {/* Error Message */}
          {error && (
            <div className="bg-destructive/10 border border-destructive text-destructive text-sm p-3 rounded-lg">
              {error}
            </div>
          )}

          {/* Stop Loss Section */}
          <div className="border rounded-lg p-4 space-y-3">
            <h3 className="font-semibold text-base">Risk Management - Stop Loss</h3>
            <div className="space-y-2">
              <label className="block text-sm font-medium">
                Stop Loss Price
                <span className="text-destructive ml-1">*</span>
              </label>
              <div className="flex gap-2">
                <Input
                  type="number"
                  placeholder="e.g., 43500"
                  value={stopLoss}
                  onChange={(e) => {
                    setStopLoss(e.target.value)
                    setError(null)
                  }}
                  step="0.01"
                  min="0"
                  disabled={isLoading}
                  className="flex-1"
                />
                <span className="text-sm text-muted-foreground pt-2">$</span>
              </div>
              <p className="text-xs text-muted-foreground">
                {position.side === "LONG"
                  ? `Must be below entry price ($${position.entryPrice})`
                  : `Must be above entry price ($${position.entryPrice})`}
              </p>
            </div>
          </div>

          {/* Take Profit Section */}
          <div className="border rounded-lg p-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-base">Take Profit Levels</h3>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddTPLevel}
                disabled={isLoading}
                className="gap-1"
              >
                <Plus className="h-4 w-4" />
                Add Level
              </Button>
            </div>

            <div className="space-y-3">
              {takeProfits.map((tp, index) => (
                <div key={index} className="border rounded-lg p-3 space-y-2 bg-muted/30">
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-sm font-medium">
                      Take Profit Level {index + 1}
                      <span className="text-destructive ml-1">*</span>
                    </label>
                    {takeProfits.length > 1 && (
                      <button
                        onClick={() => handleRemoveTPLevel(index)}
                        disabled={isLoading}
                        className="text-destructive hover:text-destructive/80 disabled:opacity-50"
                      >
                        <X className="h-4 w-4" />
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    {/* Price Input */}
                    <div className="space-y-1">
                      <label className="text-xs text-muted-foreground">Price</label>
                      <div className="flex gap-1">
                        <Input
                          type="number"
                          placeholder="e.g., 47000"
                          value={tp.price}
                          onChange={(e) => handleUpdateTPLevel(index, "price", e.target.value)}
                          step="0.01"
                          min="0"
                          disabled={isLoading}
                          className="flex-1"
                        />
                        <span className="text-xs text-muted-foreground pt-2">$</span>
                      </div>
                    </div>

                    {/* Quantity Input */}
                    <div className="space-y-1">
                      <label className="text-xs text-muted-foreground">Quantity</label>
                      <Input
                        type="number"
                        placeholder="e.g., 0.5"
                        value={tp.quantity}
                        onChange={(e) => handleUpdateTPLevel(index, "quantity", e.target.value)}
                        step="0.00000001"
                        min="0"
                        disabled={isLoading}
                      />
                    </div>
                  </div>

                  {position.side === "LONG" ? (
                    <p className="text-xs text-muted-foreground">
                      Price must be above entry ($${position.entryPrice})
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      Price must be below entry ($${position.entryPrice})
                    </p>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isLoading}
          >
            Cancel
          </Button>
          <Button onClick={validateAndSave} disabled={isLoading}>
            {isLoading ? "Updating..." : "Update Position"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
