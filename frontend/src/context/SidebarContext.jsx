/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState } from 'react'

const SidebarContext = createContext(null)

export function SidebarProvider({ children }) {
  const [isOpen, setIsOpen] = useState(true)
  const [isHover, setIsHover] = useState(false)
  const [isMobileOpen, setIsMobileOpen] = useState(false)

  const getOpenState = () => isOpen || isHover
  const toggleOpen = () => setIsOpen((previous) => !previous)
  const openMobile = () => setIsMobileOpen(true)
  const closeMobile = () => setIsMobileOpen(false)

  return (
    <SidebarContext.Provider
      value={{
        isOpen,
        isHover,
        isMobileOpen,
        setIsOpen,
        setIsHover,
        setIsMobileOpen,
        getOpenState,
        toggleOpen,
        openMobile,
        closeMobile,
      }}
    >
      {children}
    </SidebarContext.Provider>
  )
}

export function useSidebar() {
  const context = useContext(SidebarContext)

  if (!context) {
    throw new Error('useSidebar must be used within SidebarProvider')
  }

  return context
}
