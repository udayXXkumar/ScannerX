export default function HoverTooltip({ label, children, className = '', align = 'center' }) {
  const alignmentClass =
    align === 'right'
      ? 'left-auto right-0 translate-x-0'
      : align === 'left'
        ? 'left-0 translate-x-0'
        : 'left-1/2 -translate-x-1/2'

  return (
    <div className={`group/tooltip relative inline-flex ${className}`}>
      {children}
      <span
        className={`pointer-events-none absolute top-full z-50 mt-2 whitespace-nowrap rounded-xl border border-white/10 bg-[#121214] px-3 py-1.5 text-xs font-medium text-white opacity-0 shadow-[0_14px_30px_rgba(0,0,0,0.28)] transition-all duration-150 group-hover/tooltip:translate-y-0 group-hover/tooltip:opacity-100 group-focus-within/tooltip:translate-y-0 group-focus-within/tooltip:opacity-100 ${alignmentClass}`}
      >
        {label}
      </span>
    </div>
  )
}
