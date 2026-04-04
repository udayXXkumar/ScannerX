import { Link } from 'react-router-dom'

export default function TermsConsentField({
  checked,
  onChange,
  error,
  id = 'terms-consent',
}) {
  return (
    <div className="space-y-2">
      <label
        htmlFor={id}
        className="flex items-start gap-3 text-sm text-zinc-300"
      >
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={onChange}
          className="mt-1 h-4 w-4 shrink-0 rounded-sm border border-white/20 bg-[#0c0c0e] text-[#60dfb2] focus:ring-2 focus:ring-[#60dfb2]/30"
        />
        <span className="leading-6">
          I agree to the ScannerX{' '}
          <Link to="/terms" target="_blank" rel="noreferrer" className="app-link font-medium">
            terms &amp; conditions
          </Link>
          .
        </span>
      </label>

      {error ? <p className="text-sm text-rose-200">{error}</p> : null}
    </div>
  )
}
