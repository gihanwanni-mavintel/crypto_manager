'use client'

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ViewSignalDialog } from "@/components/view-signal-dialog"
import { EditSignalDialog } from "@/components/edit-signal-dialog"
import type { Signal } from "@/types/trading"
import { TrendingUp, TrendingDown, Clock, Eye, Pencil } from "lucide-react"
import { useToast } from "@/hooks/use-toast"

interface TelegramSignalsProps {
  signals: Signal[]
  onSignalUpdate?: (updatedSignal: Signal) => Promise<void>
}

export function TelegramSignals({ signals, onSignalUpdate }: TelegramSignalsProps) {
  const { toast } = useToast()
  const [viewDialogOpen, setViewDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [selectedSignal, setSelectedSignal] = useState<Signal | null>(null)

  const handleViewClick = (signal: Signal) => {
    setSelectedSignal(signal)
    setViewDialogOpen(true)
  }

  const handleEditClick = (signal: Signal) => {
    setSelectedSignal(signal)
    setEditDialogOpen(true)
  }

  const handleSaveChanges = async (updatedSignal: Signal) => {
    try {
      if (onSignalUpdate) {
        await onSignalUpdate(updatedSignal)
      }
      // Update local state
      const signalIndex = signals.findIndex((s) => s.id === updatedSignal.id)
      if (signalIndex !== -1) {
        signals[signalIndex] = updatedSignal
      }
    } catch (error) {
      throw error
    }
  }

  return (
    <>
      <div className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div>
            <h2 className="text-lg sm:text-2xl font-bold text-foreground">Telegram Signals</h2>
            <p className="text-xs sm:text-sm text-muted-foreground">Latest trading signals from your channels</p>
          </div>
          <Badge variant="outline" className="text-xs w-fit">
            {signals.filter((s) => s.status === "active").length} Active
          </Badge>
        </div>

        <div className="grid gap-3 sm:gap-4 grid-cols-1 md:grid-cols-2 lg:grid-cols-3">
          {signals.map((signal) => (
            <Card key={signal.id} className="relative overflow-hidden flex flex-col">
              <div
                className={`absolute top-0 left-0 w-1 h-full ${
                  signal.action === "BUY" ? "bg-success" : "bg-destructive"
                }`}
              />

              <CardHeader className="pb-2 sm:pb-3">
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-1 sm:gap-2">
                  <div className="min-w-0">
                    <CardTitle className="text-base sm:text-lg font-bold truncate">{signal.pair}</CardTitle>
                    <CardDescription className="text-xs truncate">{signal.source}</CardDescription>
                  </div>
                  <Badge
                    variant={signal.action === "BUY" ? "default" : "destructive"}
                    className="flex items-center gap-1 text-xs whitespace-nowrap flex-shrink-0"
                  >
                    {signal.action === "BUY" ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                    {signal.action}
                  </Badge>
                </div>
              </CardHeader>

              <CardContent className="space-y-2 sm:space-y-3 flex-grow">
                <div className="grid grid-cols-2 gap-2 text-xs sm:text-sm">
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">Entry</p>
                    <p className="font-mono font-semibold truncate">${signal.entry.toLocaleString()}</p>
                  </div>
                  <div className="min-w-0">
                    <p className="text-muted-foreground text-xs">Stop Loss</p>
                    <p className="font-mono font-semibold text-destructive truncate">${signal.stopLoss.toLocaleString()}</p>
                  </div>
                </div>

                {signal.leverage && (
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground text-xs">Leverage:</span>
                    <Badge variant="secondary" className="font-semibold text-xs">
                      {signal.leverage}x
                    </Badge>
                  </div>
                )}

                <div>
                  <p className="text-muted-foreground text-xs mb-1">Take Profit Targets</p>
                  <div className="flex gap-1 flex-wrap">
                    {signal.takeProfit.map((tp, idx) => (
                      <Badge key={idx} variant="outline" className="font-mono text-xs">
                        ${tp.toLocaleString()}
                      </Badge>
                    ))}
                  </div>
                </div>

              </CardContent>

              {/* Footer with Time and Action Buttons */}
              <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground px-3 sm:px-4 py-2 border-t border-border">
                <div className="flex items-center gap-1">
                  <Clock className="h-3 w-3" />
                  {signal.timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleViewClick(signal)}
                    className="h-8 w-8 p-0"
                    title="View signal details"
                  >
                    <Eye className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleEditClick(signal)}
                    className="h-8 w-8 p-0"
                    title="Edit signal"
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </div>

      {/* View Dialog */}
      <ViewSignalDialog signal={selectedSignal} open={viewDialogOpen} onOpenChange={setViewDialogOpen} />

      {/* Edit Dialog */}
      <EditSignalDialog
        signal={selectedSignal}
        open={editDialogOpen}
        onOpenChange={setEditDialogOpen}
        onSave={handleSaveChanges}
      />
    </>
  )
}
