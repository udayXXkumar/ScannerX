export function ScannerXWordmark({ size, width = 258, height, className = '', ...props }) {
  return (
    <svg
      className={className}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 860 120"
      fill="none"
      height={size || height}
      width={size || width}
      aria-label="ScannerX"
      role="img"
      preserveAspectRatio="xMidYMid meet"
      {...props}
    >
      <g transform="translate(430 0) skewX(-14) translate(-430 0)">
        <text
          x="430"
          y="87"
          fill="currentColor"
          fontFamily="Manrope, Inter, system-ui, sans-serif"
          fontSize="82"
          fontStyle="italic"
          fontWeight="800"
          letterSpacing="-10"
          textAnchor="middle"
        >
          SCANNERX
        </text>
      </g>
    </svg>
  )
}

export function ScannerXMark({ size, width = 42, height, className = '', ...props }) {
  return (
    <svg
      className={className}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 148 148"
      fill="none"
      height={size || height}
      width={size || width}
      aria-label="ScannerX"
      role="img"
      preserveAspectRatio="xMidYMid meet"
      {...props}
    >
      <rect
        x="10"
        y="10"
        width="128"
        height="128"
        rx="36"
        stroke="currentColor"
        strokeOpacity="0.2"
        strokeWidth="10"
      />
      <g transform="translate(74 0) skewX(-14) translate(-74 0)">
        <text
          x="74"
          y="91"
          fill="currentColor"
          fontFamily="Manrope, Inter, system-ui, sans-serif"
          fontSize="52"
          fontStyle="italic"
          fontWeight="800"
          letterSpacing="-7"
          textAnchor="middle"
        >
          SX
        </text>
      </g>
    </svg>
  )
}

export const ProwlerExtended = ScannerXWordmark
export const ProwlerShort = ScannerXMark
