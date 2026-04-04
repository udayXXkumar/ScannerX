/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Prowler color theme
        prowler: {
          green: "#9FD655",
          "green-medium": "#09BF3D",
          purple: "#5001d0",
          orange: "#f69000",
          blue: {
            800: "#1e293bff",
            400: "#1A202C",
          },
          grey: {
            medium: "#353a4d",
            light: "#868994",
            600: "#64748b",
          },
        },
        // Severity colors for findings
        severity: {
          critical: "#AC1954",
          high: "#F31260",
          medium: "#FA7315",
          low: "#fcd34d",
        },
        // System status colors
        system: {
          success: "#09BF3D",
          error: "#E11D48",
          warning: "#FBBF24",
          info: "#7C3AED",
        },
      },
      backgroundColor: {
        "bg-base": "#0f172a",
        "bg-panel": "#1e293b",
      },
      borderColor: {
        "border-subtle": "#334155",
      },
      animation: {
        "fade-in": "fadeIn 0.2s ease-out",
        "fade-out": "fadeOut 0.2s ease-in",
        "slide-in": "slideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        "slide-out": "slideOut 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        expand: "expand 0.4s linear",
        collapse: "collapse 0.4s linear",
      },
      keyframes: {
        fadeIn: {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        fadeOut: {
          "0%": { opacity: "1" },
          "100%": { opacity: "0" },
        },
        slideIn: {
          "0%": { transform: "translateX(-100%)", opacity: "0" },
          "100%": { transform: "translateX(0)", opacity: "1" },
        },
        slideOut: {
          "0%": { transform: "translateX(0)", opacity: "1" },
          "100%": { transform: "translateX(-100%)", opacity: "0" },
        },
        expand: {
          "0%": { maxHeight: "0", overflow: "hidden" },
          "100%": { maxHeight: "100vh", overflow: "visible" },
        },
        collapse: {
          "0%": { maxHeight: "100vh", overflow: "hidden" },
          "100%": { maxHeight: "0", overflow: "hidden" },
        },
      },
    },
  },
  plugins: [],
  darkMode: "class",
}
