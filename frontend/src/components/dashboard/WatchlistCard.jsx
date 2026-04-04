import { ArrowUpRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export default function WatchlistCard({
  title,
  items = [],
  ctaLabel,
  ctaTo,
  emptyMessage = 'No data available.',
}) {
  const navigate = useNavigate()

  return (
    <section className="surface-card flex min-h-[264px] flex-col px-5 pt-4 pb-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h3 className="text-lg font-medium text-white">{title}</h3>
        {ctaLabel && ctaTo ? (
          <button
            onClick={() => navigate(ctaTo)}
            className="inline-flex items-center gap-2 text-sm font-medium text-emerald-200 transition-colors hover:text-emerald-100"
          >
            {ctaLabel}
            <ArrowUpRight size={16} />
          </button>
        ) : null}
      </div>

      {items.length === 0 ? (
        <div className="surface-card-inner flex flex-1 items-center justify-center px-6 py-8">
          <p className="text-sm text-zinc-400">{emptyMessage}</p>
        </div>
      ) : (
        <div className="surface-card-inner flex flex-1 flex-col overflow-hidden">
          {items.map((item, index) => (
            <button
              key={item.key}
              type="button"
              onClick={() => {
                if (item.to) {
                  navigate(item.to)
                }
              }}
              className={`flex items-center justify-between gap-4 px-4 py-3 text-left ${
                item.to ? 'transition-colors hover:bg-white/[0.03]' : ''
              } ${index !== items.length - 1 ? 'border-b border-white/5' : ''}`}
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-medium text-zinc-100">{item.label}</p>
                  {item.badge ? (
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase ${item.badgeClass}`}>
                      {item.badge}
                    </span>
                  ) : null}
                </div>
                {item.meta ? (
                  <p className="mt-1 truncate text-xs leading-5 text-zinc-500">{item.meta}</p>
                ) : null}
              </div>

              <div className={`shrink-0 text-sm font-semibold ${item.tone || 'text-zinc-200'}`}>
                {item.value}
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
