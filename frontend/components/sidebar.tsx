"use client"

import { useState } from "react"
import { cn } from "@/lib/utils"
import { Radio, TrendingUp, ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"

interface SidebarProps {
  activeSection: string
  onSectionChange: (section: string) => void
}

const navItems = [
  {
    id: "signals",
    label: "Telegram Signals",
    icon: Radio,
  },
  {
    id: "positions",
    label: "Active Positions",
    icon: TrendingUp,
  },
]

export function Sidebar({ activeSection, onSectionChange }: SidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState(false)

  return (
    <aside
      className={cn(
        "border-r border-border bg-card h-[calc(100vh-73px)] sticky top-[73px] transition-all duration-300",
        isCollapsed ? "w-16" : "w-64",
      )}
    >
      <nav className="p-4 space-y-2">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive = activeSection === item.id

          return (
            <button
              key={item.id}
              onClick={() => onSectionChange(item.id)}
              className={cn(
                "w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                isCollapsed && "justify-center px-0",
              )}
            >
              <Icon className="h-5 w-5 flex-shrink-0" />
              {!isCollapsed && <span>{item.label}</span>}
            </button>
          )
        })}
      </nav>

      <div className={cn("px-4 pb-4", isCollapsed && "px-2")}>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setIsCollapsed(!isCollapsed)}
          className={cn("w-full", isCollapsed && "px-0 justify-center")}
        >
          {isCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          {!isCollapsed && <span className="ml-2">Collapse</span>}
        </Button>
      </div>
    </aside>
  )
}
