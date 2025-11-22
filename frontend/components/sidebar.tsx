"use client"

import { useState } from "react"
import { cn } from "@/lib/utils"
import { Radio, TrendingUp, ArrowLeftRight, History, Settings, ChevronLeft, ChevronRight, LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"
import { authAPI } from "@/lib/api"

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
  {
    id: "trading",
    label: "Manual Trading",
    icon: ArrowLeftRight,
  },
  {
    id: "management",
    label: "Trade Management",
    icon: Settings,
  },
  {
    id: "history",
    label: "Trade History",
    icon: History,
  },
]

export function Sidebar({ activeSection, onSectionChange }: SidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState(false)

  const handleLogout = () => {
    authAPI.logout()
    window.location.href = "/"
  }

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

      <div className="absolute bottom-0 left-0 right-0 border-t border-border bg-card">
        <div className={cn("px-4 py-4 space-y-2", isCollapsed && "px-2")}>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsCollapsed(!isCollapsed)}
            className={cn("w-full", isCollapsed && "px-0 justify-center")}
          >
            {isCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
            {!isCollapsed && <span className="ml-2">Collapse</span>}
          </Button>

          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className={cn(
              "w-full text-destructive hover:bg-destructive/10 hover:text-destructive",
              isCollapsed && "px-0 justify-center"
            )}
          >
            <LogOut className="h-5 w-5 flex-shrink-0" />
            {!isCollapsed && <span className="ml-2">Logout</span>}
          </Button>
        </div>
      </div>
    </aside>
  )
}
