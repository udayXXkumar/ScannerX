import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Check, ChevronDown } from 'lucide-react'
import { cn } from '../../lib/utils'

const FILTER_TRIGGER_CLASSES =
  'inline-flex h-12 shrink-0 items-center justify-between gap-3 rounded-xl border border-white/10 bg-white/[0.04] px-4 text-left text-sm text-zinc-200 outline-none transition-colors hover:border-white/16 hover:bg-white/[0.05] focus-visible:border-prowler-green/35 focus-visible:bg-white/[0.05] disabled:cursor-not-allowed disabled:opacity-60'

const FIELD_TRIGGER_CLASSES =
  'flex min-h-[52px] w-full items-center justify-between gap-3 rounded-[0.875rem] border border-[rgba(76,82,96,0.5)] bg-[rgba(26,31,42,0.72)] px-4 py-[0.85rem] text-left text-sm text-slate-50 outline-none shadow-[inset_0_1px_0_rgba(255,255,255,0.03)] transition-[border-color,background-color,box-shadow] hover:border-white/16 focus-visible:border-[rgba(92,223,178,0.4)] focus-visible:bg-[rgba(24,29,39,0.9)] focus-visible:shadow-[0_0_0_1px_rgba(92,223,178,0.14)] disabled:cursor-not-allowed disabled:opacity-60'

const MENU_Z_INDEX = 160

export default function DarkSelect({
  value,
  onChange,
  options,
  placeholder = 'Select an option',
  variant = 'filter',
  className,
  menuClassName,
  disabled = false,
  style,
}) {
  const triggerRef = useRef(null)
  const menuRef = useRef(null)
  const optionRefs = useRef([])
  const listboxId = useId()

  const [isOpen, setIsOpen] = useState(false)
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const [menuPosition, setMenuPosition] = useState({ top: 0, left: 0, width: 0, maxHeight: 280 })

  const normalizedValue = String(value ?? '')
  const selectedIndex = useMemo(
    () => options.findIndex((option) => String(option.value) === normalizedValue),
    [normalizedValue, options],
  )
  const selectedOption = selectedIndex >= 0 ? options[selectedIndex] : null
  const triggerClasses = variant === 'field' ? FIELD_TRIGGER_CLASSES : FILTER_TRIGGER_CLASSES

  const syncMenuPosition = useCallback(() => {
    if (!triggerRef.current) {
      return
    }

    const rect = triggerRef.current.getBoundingClientRect()
    const estimatedHeight = Math.min(320, Math.max(168, options.length * 44 + 16))
    const spaceBelow = window.innerHeight - rect.bottom - 12
    const spaceAbove = rect.top - 12
    const openUpwards = spaceBelow < Math.min(estimatedHeight, 240) && spaceAbove > spaceBelow
    const maxHeight = Math.max(144, openUpwards ? spaceAbove : spaceBelow)
    const top = openUpwards
      ? Math.max(8, rect.top - Math.min(estimatedHeight, maxHeight) - 8)
      : rect.bottom + 8
    const left = Math.min(rect.left, window.innerWidth - Math.min(rect.width, window.innerWidth - 16) - 8)

    setMenuPosition({
      top,
      left: Math.max(8, left),
      width: Math.min(rect.width, window.innerWidth - 16),
      maxHeight,
    })
  }, [options.length])

  const closeMenu = () => {
    setIsOpen(false)
    setHighlightedIndex(-1)
  }

  const openMenu = () => {
    if (disabled || options.length === 0) {
      return
    }

    setHighlightedIndex(selectedIndex >= 0 ? selectedIndex : 0)
    setIsOpen(true)
  }

  const selectOption = (option) => {
    onChange?.(option.value)
    closeMenu()
    triggerRef.current?.focus()
  }

  useEffect(() => {
    if (!isOpen) {
      return undefined
    }

    syncMenuPosition()
    menuRef.current?.focus()

    const handlePointerDown = (event) => {
      const menuNode = menuRef.current
      const triggerNode = triggerRef.current
      if (menuNode?.contains(event.target) || triggerNode?.contains(event.target)) {
        return
      }

      closeMenu()
    }

    const handleReposition = () => syncMenuPosition()

    window.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('resize', handleReposition)
    window.addEventListener('scroll', handleReposition, true)

    return () => {
      window.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('resize', handleReposition)
      window.removeEventListener('scroll', handleReposition, true)
    }
  }, [isOpen, selectedIndex, syncMenuPosition])

  useEffect(() => {
    if (!isOpen || highlightedIndex < 0) {
      return
    }

    optionRefs.current[highlightedIndex]?.scrollIntoView({ block: 'nearest' })
  }, [highlightedIndex, isOpen])

  const handleTriggerKeyDown = (event) => {
    if (disabled) {
      return
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openMenu()
    }
  }

  const handleMenuKeyDown = (event) => {
    if (!isOpen || options.length === 0) {
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      closeMenu()
      triggerRef.current?.focus()
      return
    }

    if (event.key === 'Tab') {
      closeMenu()
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlightedIndex((previous) => (previous + 1) % options.length)
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlightedIndex((previous) => (previous <= 0 ? options.length - 1 : previous - 1))
      return
    }

    if (event.key === 'Home') {
      event.preventDefault()
      setHighlightedIndex(0)
      return
    }

    if (event.key === 'End') {
      event.preventDefault()
      setHighlightedIndex(options.length - 1)
      return
    }

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      const option = options[highlightedIndex]
      if (option && !option.disabled) {
        selectOption(option)
      }
    }
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        onClick={() => (isOpen ? closeMenu() : openMenu())}
        onKeyDown={handleTriggerKeyDown}
        className={cn(triggerClasses, className)}
        style={style}
      >
        <span className={cn('truncate', selectedOption ? 'text-zinc-200' : 'text-slate-500')}>
          {selectedOption?.label ?? placeholder}
        </span>
        <ChevronDown
          size={16}
          className={cn('shrink-0 text-slate-400 transition-transform', isOpen ? 'rotate-180' : '')}
        />
      </button>

      {isOpen && typeof document !== 'undefined'
        ? createPortal(
            <div
              ref={menuRef}
              id={listboxId}
              role="listbox"
              tabIndex={-1}
              aria-activedescendant={highlightedIndex >= 0 ? `${listboxId}-option-${highlightedIndex}` : undefined}
              onKeyDown={handleMenuKeyDown}
              className={cn(
                'overflow-auto rounded-2xl border border-white/10 bg-[#161214]/96 p-2 shadow-[0_24px_64px_rgba(0,0,0,0.45)] backdrop-blur-2xl',
                menuClassName,
              )}
              style={{
                position: 'fixed',
                top: menuPosition.top,
                left: menuPosition.left,
                width: menuPosition.width,
                maxHeight: menuPosition.maxHeight,
                zIndex: MENU_Z_INDEX,
              }}
            >
              {options.map((option, index) => {
                const isSelected = String(option.value) === normalizedValue
                const isHighlighted = highlightedIndex === index

                return (
                  <button
                    key={`${listboxId}-${String(option.value)}`}
                    id={`${listboxId}-option-${index}`}
                    ref={(node) => {
                      optionRefs.current[index] = node
                    }}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    disabled={option.disabled}
                    onMouseEnter={() => setHighlightedIndex(index)}
                    onClick={() => !option.disabled && selectOption(option)}
                    className={cn(
                      'flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors',
                      option.disabled
                        ? 'cursor-not-allowed text-slate-600'
                        : isHighlighted
                          ? 'bg-[#60dfb2]/14 text-white'
                          : 'text-zinc-200 hover:bg-white/[0.05]',
                    )}
                  >
                    <span className="truncate">{option.label}</span>
                    {isSelected ? <Check size={15} className="shrink-0 text-prowler-green" /> : null}
                  </button>
                )
              })}
            </div>,
            document.body,
          )
        : null}
    </>
  )
}
