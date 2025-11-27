"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { ViewPositionDialog } from "@/components/view-position-dialog"
import { EditPositionDialog } from "@/components/edit-position-dialog"
import type { Position } from "@/types/trading"
import { TrendingUp, TrendingDown, X, Eye, Pencil } from "lucide-react"
import { positionsAPI } from "@/lib/api"

interface PositionManagementProps {
  positions: Position[]
  onClosePosition: (id: string) => void
  onUpdatePosition?: (position: Position) => Promise<void>
  onPositionsClosed?: (ids: string[]) => void
}

export function PositionManagement({ positions, onClosePosition, onUpdatePosition, onPositionsClosed }: PositionManagementProps) {
  const [viewDialogOpen, setViewDialogOpen] = useState(false)
  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [selectedPosition, setSelectedPosition] = useState<Position | null>(null)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)
  const [positionToClose, setPositionToClose] = useState<Position | null>(null)
  const [isClosing, setIsClosing] = useState(false)
  const [closeError, setCloseError] = useState<string | null>(null)

  const handleViewClick = (position: Position) => {
    setSelectedPosition(position)
    setViewDialogOpen(true)
  }

  const handleEditClick = (position: Position) => {
    setSelectedPosition(position)
    setEditDialogOpen(true)
  }

  const handleSaveChanges = async (updatedPosition: Position) => {
    try {
      if (onUpdatePosition) {
        await onUpdatePosition(updatedPosition)
      }
    } catch (error) {
      throw error
    }
  }

  const handleClosePositionClick = (position: Position) => {
    setPositionToClose(position)
    setCloseConfirmOpen(true)
    setCloseError(null)
  }

  const handleConfirmClose = async () => {
    if (!positionToClose) return

    try {
      setIsClosing(true)
      setCloseError(null)

      // Call API to close position on Binance
      if (positionToClose.binanceSymbol) {
        await positionsAPI.closePosition(
          positionToClose.binanceSymbol,
          positionToClose.quantity,
          positionToClose.side
        )
      }

      // Call the original onClosePosition handler
      onClosePosition(positionToClose.id)

      // Notify parent of closed position
      if (onPositionsClosed) {
        onPositionsClosed([positionToClose.id])
      }

      setCloseConfirmOpen(false)
      setPositionToClose(null)
    } catch (error) {
      setCloseError(error instanceof Error ? error.message : "Failed to close position")
    } finally {
      setIsClosing(false)
    }
  }

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

                <div className="flex gap-2">
                  <Button variant="outline" size="sm" className="flex-1" onClick={() => handleViewClick(position)}>
                    <Eye className="h-4 w-4 mr-2" />
                    View Details
                  </Button>
                  <Button variant="outline" size="sm" className="flex-1" onClick={() => handleEditClick(position)}>
                    <Pencil className="h-4 w-4 mr-2" />
                    Edit
                  </Button>
                </div>

                <Button
                  variant="destructive"
                  size="sm"
                  className="w-full mt-2"
                  onClick={() => handleClosePositionClick(position)}
                >
                  <X className="h-4 w-4 mr-2" />
                  Close Position
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* View Position Dialog */}
      <ViewPositionDialog position={selectedPosition} open={viewDialogOpen} onOpenChange={setViewDialogOpen} />

      {/* Edit Position Dialog */}
      <EditPositionDialog
        position={selectedPosition}
        open={editDialogOpen}
        onOpenChange={setEditDialogOpen}
        onSave={handleSaveChanges}
      />

      {/* Close Position Confirmation Dialog */}
      <AlertDialog open={closeConfirmOpen} onOpenChange={setCloseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogTitle>Close Position?</AlertDialogTitle>
          <AlertDialogDescription className="space-y-2">
            <p>
              Are you sure you want to close your {positionToClose?.side} position in{" "}
              <span className="font-semibold">{positionToClose?.pair}</span>?
            </p>
            {positionToClose && (
              <div className="bg-muted p-3 rounded-lg text-sm space-y-1 text-foreground">
                <div className="flex justify-between">
                  <span>Entry Price:</span>
                  <span className="font-mono">${positionToClose.entryPrice.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span>Current Price:</span>
                  <span className="font-mono">${positionToClose.currentPrice.toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span>P&L:</span>
                  <span className={`font-mono ${positionToClose.pnl >= 0 ? "text-success" : "text-destructive"}`}>
                    {positionToClose.pnl >= 0 ? "+" : ""}${positionToClose.pnl.toFixed(2)}
                  </span>
                </div>
              </div>
            )}
            <p className="text-xs text-muted-foreground">This action will close the position on Binance.</p>
            {closeError && (
              <div className="bg-destructive/10 border border-destructive text-destructive text-xs p-2 rounded">
                {closeError}
              </div>
            )}
          </AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isClosing}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmClose}
              disabled={isClosing}
              className="bg-destructive hover:bg-destructive/90"
            >
              {isClosing ? "Closing..." : "Close Position"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
