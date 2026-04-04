import { ArrowLeft } from 'lucide-react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ScannerXWordmark } from '../components/icons/ProwlerIcons';
import { cn } from '../lib/utils';

const PublicLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const isTermsPage = location.pathname === '/terms';

  return (
    <div className="relative min-h-screen w-full overflow-hidden bg-[#08090a]">
      <div
        className="pointer-events-none absolute inset-0 opacity-70"
        style={{
          backgroundImage: 'radial-gradient(rgba(72,224,214,0.52) 1.1px, transparent 1.1px)',
          backgroundSize: '16px 16px',
          maskImage:
            'radial-gradient(ellipse 36% 54% at 50% 50%, rgba(0,0,0,1) 0%, rgba(0,0,0,0.92) 60%, rgba(0,0,0,0) 100%)',
        }}
      />

      <div className="pointer-events-none absolute left-[-12%] top-[10%] h-[540px] w-[540px] rounded-full bg-emerald-300/10 blur-3xl" />
      <div className="pointer-events-none absolute right-[-12%] top-[18%] h-[560px] w-[560px] rounded-full bg-cyan-300/9 blur-3xl" />

      <div className="auth-page-shell">
        <header className="auth-page-header">
          <button
            type="button"
            onClick={() => navigate('/')}
            className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-white/10 bg-white/[0.03] text-zinc-300 transition-colors hover:border-white/20 hover:bg-white/[0.05] hover:text-white"
            aria-label="Back to home"
          >
            <ArrowLeft size={18} />
          </button>

          <div className="auth-page-logo">
            <ScannerXWordmark width={420} className="h-auto w-[286px] sm:w-[322px]" />
          </div>

          <div className="h-11 w-11" aria-hidden="true" />
        </header>

        <main className={cn('auth-content', isTermsPage ? 'items-start pb-6' : 'items-center')}>
          <div className={cn('auth-card', isTermsPage && 'max-w-4xl px-6 py-8 sm:px-8 sm:py-10')}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};
export default PublicLayout;
