import { useState } from 'react'
import {
  Activity,
  ArrowRight,
  BellRing,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  CircleUserRound,
  Download,
  Menu,
  Radar,
  Search,
  ShieldCheck,
  Sparkles,
  Target,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

const heroGlowImages = {
  left: 'https://cdn.prod.website-files.com/68c4ec3f9fb7b154fbcb6e36/68c545e998761777c7891073_Group%2074.avif',
  right: 'https://cdn.prod.website-files.com/68c4ec3f9fb7b154fbcb6e36/68c545e9f7eb7efd0118b6bd_Group%2073.avif',
}

const grillShadedCells = [
  { col: 1, row: 8, opacity: 0.038 },
  { col: 4, row: 2, opacity: 0.05 },
  { col: 8, row: 11, opacity: 0.036 },
  { col: 11, row: 6, opacity: 0.044 },
  { col: 15, row: 9, opacity: 0.04 },
  { col: 19, row: 3, opacity: 0.034 },
  { col: 23, row: 12, opacity: 0.038 },
  { col: 26, row: 7, opacity: 0.05 },
  { col: 29, row: 4, opacity: 0.04 },
  { col: 31, row: 1, opacity: 0.046 },
  { col: 30, row: 10, opacity: 0.034 },
  { col: 6, row: 14, opacity: 0.032 },
]

const navItems = [
  { label: 'Platform', href: '#platform', hasDropdown: true },
  { label: 'Use Cases', href: '#use-cases', hasDropdown: true },
  { label: 'Learn', href: '#learn', hasDropdown: true },
  { label: 'Pricing', href: '#pricing' },
  { label: 'Docs', href: '#docs' },
]

const capabilityChips = ['Live Findings', 'AI Finding Intel', 'Queued Runs', 'Reports', 'Notifications', 'Rescans']

const platformHighlights = [
  'Create a target with a base URL, tags, and project grouping',
  'Submit the URL once and run the scanner stack directly against it',
  'Queue asynchronous runs and stream findings back over WebSocket',
  'AI enrichment turns raw scanner output into clearer descriptions and exploit context',
  'Compare scan history, export reports, and schedule recurring rescans',
]

const outcomeRows = [
  {
    eyebrow: 'Target Onboarding',
    title: 'Add a URL once and make it immediately ready for scanning',
    body: 'ScannerX now follows the simpler flow you asked for: create the target, store the URL, and move straight into the scanning workflow.',
    bullets: [
      'Named targets keep results attached to the correct URL and project group',
      'Each saved URL is treated as ready for direct tool execution right away',
    ],
    visual: 'target',
  },
  {
    eyebrow: 'Automated Execution',
    title: 'Run a focused web assessment across the automated scan workflow',
    body: 'Once a target is saved, ScannerX can queue the run and hand work to the orchestrator across discovery, security checks, XSS review, and injection analysis.',
    bullets: [
      'The queue keeps scan launches responsive while background work continues',
      'The landing page copy reflects the real staged execution path in the app',
    ],
    visual: 'scan',
  },
  {
    eyebrow: 'Live Findings Workflow',
    title: 'Watch findings arrive, update states, and compare runs without leaving the workspace',
    body: 'The application already supports live updates, findings review, status changes, comments, comparison views, and notifications, so the public site now speaks directly to that operator workflow.',
    bullets: [
      'WebSocket updates surface progress and new findings during the scan',
      'Comparison helps separate newly introduced issues from already known ones',
    ],
    visual: 'findings',
  },
  {
    eyebrow: 'AI Finding Intelligence',
    title: 'Turn raw scanner output into clearer security explanations your team can act on',
    body: 'ScannerX now enriches saved findings with AI-assisted descriptions and defender-safe exploit context so operators can understand what the issue means before they open an export.',
    bullets: [
      'AI explanations stay defensive and focus on context, prerequisites, and impact',
      'Enrichment is persisted with the finding so reports and reviews stay consistent',
    ],
    visual: 'findings',
  },
  {
    eyebrow: 'Reports And Rescans',
    title: 'Export results, schedule recurring scans, and keep important URLs under watch',
    body: 'After a run completes, ScannerX already exposes the outputs teams care about: downloadable reports, scheduled rescans, and a clean handoff path for whoever needs to act on the result.',
    bullets: [
      'CSV, JSON, executive HTML, and detailed HTML exports are already supported',
      'Cron-based schedules let the same URL be rescanned automatically',
    ],
    visual: 'reports',
  },
]

const learnCards = [
  {
    label: 'Step 1',
    title: 'Create the URL target once',
    body: 'ScannerX starts with a single URL-centric asset record so every scan, comparison, and report stays grouped around the same target.',
    href: '#platform',
    linkLabel: 'See target onboarding',
  },
  {
    label: 'Step 2',
    title: 'Launch the scan immediately',
    body: 'Once the URL is saved, the scanner workflow can move directly into queued execution with no extra setup step.',
    href: '#use-cases',
    linkLabel: 'See direct scan flow',
  },
  {
    label: 'Step 3',
    title: 'Review findings and export the result',
    body: 'Scans execute asynchronously while the UI receives real-time updates, surfaces findings, and exposes downloads once the run completes.',
    href: '#pricing',
    linkLabel: 'See post-scan outputs',
  },
]

const productStats = [
  {
    label: 'Coverage',
    value: 'Full',
    description: 'multi-stage web assessment flow managed by the orchestrator',
  },
  {
    label: 'Updates',
    value: 'Live',
    description: 'WebSocket events stream progress and findings back into the interface',
  },
  {
    label: 'Exports',
    value: '4',
    description: 'CSV, JSON, executive HTML, and detailed HTML report outputs',
  },
  {
    label: 'AI Assist',
    value: 'Live',
    description: 'saved findings gain clearer descriptions and defender-safe exploit context',
  },
]

const workflowCards = [
  {
    title: 'AI-Enriched Findings',
    body: 'ScannerX adds AI-assisted descriptions and safe exploit context to persisted findings so the same language follows triage and reporting.',
  },
  {
    title: 'Scan Comparison',
    body: 'Compare two runs of the same target and separate newly introduced findings from resolved or unchanged ones.',
  },
  {
    title: 'Finding Workflow',
    body: 'Update finding status, assignment, and analyst notes directly in the workspace while triage is in progress.',
  },
  {
    title: 'Downloadable Outputs',
    body: 'Export CSV and JSON reports per scan or open executive and detailed HTML summaries for handoff.',
  },
]

const footerColumns = [
  {
    title: 'Platform',
    links: [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Targets', to: '/targets' },
      { label: 'Scans', to: '/scans' },
      { label: 'Findings', to: '/findings' },
    ],
  },
  {
    title: 'Workflow',
    links: [
      { label: 'Direct Scan Flow', href: '#use-cases' },
      { label: 'Live Findings', href: '#use-cases' },
      { label: 'Reports', href: '#pricing' },
      { label: 'Schedules', to: '/schedules' },
    ],
  },
  {
    title: 'ScannerX',
    links: [
      { label: 'Create Account', to: '/register' },
      { label: 'Log In', to: '/login' },
      { label: 'Workspace', to: '/dashboard' },
      { label: 'Docs Section', href: '#docs' },
    ],
  },
]

export default function LandingPage() {
  const { token } = useAuth()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const authHref = token ? '/dashboard' : '/login'
  const authLabel = token ? 'Workspace' : 'Log in'
  const primaryHref = token ? '/targets' : '/register'
  const primaryLabel = token ? 'Launch a Live Scan' : 'Create ScannerX Account'
  const secondaryHref = token ? '/dashboard' : '/login'
  const secondaryLabel = token ? 'Open ScannerX Workspace' : 'Log In to ScannerX'
  const topCtaLabel = token ? 'Launch Scan' : 'Get Started'
  const grillCellSize = 41

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-[#050607] text-white">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundColor: '#09090a',
          backgroundImage: [
            'linear-gradient(rgba(255,255,255,0.028) 1px, transparent 1px)',
            'linear-gradient(90deg, rgba(255,255,255,0.028) 1px, transparent 1px)',
          ].join(', '),
          backgroundSize: '41px 41px',
        }}
      />
      {grillShadedCells.map((square, index) => (
        <div
          key={index}
          className="pointer-events-none absolute"
          style={{
            left: `${square.col * grillCellSize}px`,
            top: `${square.row * grillCellSize}px`,
            width: `${grillCellSize}px`,
            height: `${grillCellSize}px`,
            backgroundColor: `rgba(255, 255, 255, ${square.opacity})`,
          }}
        />
      ))}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_38%,rgba(5,6,7,0.02)_0%,rgba(5,6,7,0.14)_32%,rgba(5,6,7,0.42)_68%,rgba(5,6,7,0.72)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[180px] bg-gradient-to-b from-white/[0.03] to-transparent" />
      <div className="pointer-events-none absolute inset-0 z-[5] overflow-hidden mix-blend-screen">
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: [
              'radial-gradient(40% 76% at -6% 38%, rgba(53, 208, 128, 0.30) 0%, rgba(27, 110, 68, 0.17) 38%, rgba(5, 6, 7, 0) 78%)',
              'radial-gradient(36% 76% at 106% 34%, rgba(100, 226, 231, 0.27) 0%, rgba(48, 116, 130, 0.16) 38%, rgba(5, 6, 7, 0) 80%)',
              'radial-gradient(24% 24% at 50% 82%, rgba(74, 241, 247, 0.18) 0%, rgba(5, 6, 7, 0) 76%)',
            ].join(', '),
          }}
        />
        <div
          className="absolute inset-0 blur-2xl"
          style={{
            backgroundImage: [
              'radial-gradient(32% 66% at 4% 46%, rgba(72, 232, 161, 0.10) 0%, rgba(5, 6, 7, 0) 72%)',
              'radial-gradient(30% 64% at 98% 38%, rgba(120, 238, 255, 0.10) 0%, rgba(5, 6, 7, 0) 74%)',
            ].join(', '),
          }}
        />
      </div>
      <header className="relative z-20 px-4 pt-4 sm:px-6 lg:px-8">
        <div className="section-shell flex items-center gap-4 xl:grid xl:grid-cols-[220px_minmax(0,1fr)_300px] xl:items-center xl:gap-5 2xl:grid-cols-[236px_minmax(0,1fr)_320px]">
          <Link to="/" className="flex shrink-0 justify-center text-white xl:justify-self-start">
            <LandingScannerXWordmark className="h-auto w-[230px] sm:w-[264px] xl:w-[236px]" />
          </Link>

          <div className="hidden min-w-0 items-center justify-center xl:flex xl:justify-self-center">
            <nav className="w-fit max-w-full rounded-[20px] border border-white/[0.1] bg-[#111214]/94 p-2 shadow-[0_18px_48px_rgba(0,0,0,0.38)] backdrop-blur-xl">
              <ul className="flex flex-nowrap items-center gap-0.5 whitespace-nowrap">
                {navItems.map((item) => (
                  <li key={item.label}>
                    <a
                      href={item.href}
                      className="flex items-center gap-2 rounded-2xl px-3.5 py-3 text-[15px] font-medium whitespace-nowrap text-white/82 transition-colors hover:bg-white/[0.04] hover:text-white"
                    >
                      <span>{item.label}</span>
                      {item.hasDropdown ? <ChevronDown size={16} className="text-white/72" /> : null}
                    </a>
                  </li>
                ))}

                <li className="pl-2">
                  <Link
                    to={authHref}
                    className="flex min-w-[148px] items-center justify-center gap-2 rounded-2xl bg-white px-4 py-3 text-[15px] font-semibold text-black transition-transform hover:-translate-y-0.5"
                  >
                    <CircleUserRound size={18} />
                    <span>{authLabel}</span>
                  </Link>
                </li>
              </ul>
            </nav>
          </div>

          <div className="hidden shrink-0 items-center justify-end gap-4 xl:flex xl:justify-self-end">
            <a
              href="#pricing"
              className="flex min-w-[148px] items-center justify-center gap-3 px-1 py-3 text-white/86 transition-colors hover:text-white"
            >
              <MetricOrbitIcon />
              <span className="text-[15px]">Live Coverage</span>
            </a>

            <Link
              to={primaryHref}
              className="inline-flex min-w-[132px] items-center justify-center rounded-[12px] bg-white px-5 py-3 text-[15px] font-medium text-black transition-transform hover:-translate-y-0.5"
            >
              {topCtaLabel}
            </Link>
          </div>

          <div className="ml-auto flex items-center gap-3 xl:hidden">
            <Link
              to={authHref}
              className="rounded-[15px] bg-white px-4 py-2.5 text-sm font-semibold text-black"
            >
              {authLabel}
            </Link>
            <button
              type="button"
              onClick={() => setMobileMenuOpen((current) => !current)}
              className="flex h-11 w-11 items-center justify-center rounded-[15px] border border-white/[0.1] bg-[#111214]/94 text-white"
              aria-label="Open navigation"
            >
              <Menu size={18} />
            </button>
          </div>
        </div>

        {mobileMenuOpen ? (
          <div className="section-shell mt-4 rounded-[28px] border border-white/[0.1] bg-[#111214]/95 p-3 shadow-[0_18px_48px_rgba(0,0,0,0.38)] backdrop-blur-xl xl:hidden">
            <div className="space-y-1">
              {navItems.map((item) => (
                <a
                  key={item.label}
                  href={item.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center justify-between rounded-2xl px-4 py-3 text-sm font-medium text-white/82 transition-colors hover:bg-white/[0.04] hover:text-white"
                >
                  <span>{item.label}</span>
                  {item.hasDropdown ? <ChevronDown size={16} className="text-white/72" /> : null}
                </a>
              ))}

              <Link
                to={primaryHref}
                onClick={() => setMobileMenuOpen(false)}
                className="block rounded-2xl bg-white px-4 py-3 text-sm font-semibold text-black"
              >
                {topCtaLabel}
              </Link>
            </div>
          </div>
        ) : null}
      </header>

      <main className="relative z-10">
        <section className="px-4 pb-12 pt-12 text-center sm:px-6 lg:px-8 lg:pt-14">
          <div className="mx-auto max-w-5xl">
            <a
              href="#use-cases"
              className="mb-7 inline-flex items-center gap-3 rounded-[2rem] border border-cyan-300/20 bg-[#08181b]/90 px-1 py-1 pr-4 text-sm text-cyan-300 shadow-[0_0_0_1px_rgba(52,228,219,0.12)] backdrop-blur-md transition-transform hover:-translate-y-0.5"
            >
              <span className="rounded-full bg-cyan-300 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-black">
                New
              </span>
              <span>Watch live findings and AI-powered context arrive together</span>
              <ArrowRight size={15} />
            </a>

            <h1 className="mx-auto max-w-[42rem] text-[clamp(3rem,4.9vw,4rem)] font-medium leading-[1.08] tracking-[-0.02em] text-white">
              <span className="block">Scan ANY URL at</span>
              <span className="mt-2 block bg-gradient-to-r from-[#66f2e1] via-[#34e4db] to-[#20d3eb] bg-clip-text text-transparent">
                ScannerX Speed.
              </span>
            </h1>

            <p className="mx-auto mt-6 max-w-[32rem] text-[clamp(1rem,1.5vw,1.35rem)] leading-[1.55] text-white/64">
              Give ScannerX a URL and let the platform run web
              vulnerability checks, stream findings live, enrich each result with
              AI-powered finding intelligence, compare scan history, and export
              the result from one operator-friendly workspace.
            </p>

            <div className="mt-8 flex flex-col items-center justify-center gap-4 sm:flex-row">
              <Link
                to={primaryHref}
                className="inline-flex items-center justify-center whitespace-nowrap rounded-[0.5rem] bg-[#60e0ec] px-6 py-3 text-[1rem] font-normal text-black shadow-[0_0_0_rgba(96,224,236,0),0_2px_3.2px_rgba(96,224,236,0.08),0_12px_28px_rgba(96,224,236,0.22)] transition-transform hover:-translate-y-0.5"
              >
                {primaryLabel}
              </Link>
              <Link
                to={secondaryHref}
                className="inline-flex items-center justify-center whitespace-nowrap rounded-[0.5rem] bg-[#60e0ec] px-6 py-3 text-[1rem] font-normal text-black shadow-[0_0_0_rgba(96,224,236,0),0_2px_3.2px_rgba(96,224,236,0.08),0_12px_28px_rgba(96,224,236,0.22)] transition-transform hover:-translate-y-0.5"
              >
                {secondaryLabel}
              </Link>
            </div>

            <div className="mt-12">
              <p className="text-lg font-medium text-white/72">
                Trusted by teams securing every URL they ship
              </p>
              <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {capabilityChips.map((capability) => (
                  <div
                    key={capability}
                    className="rounded-[18px] border border-white/[0.08] bg-white/[0.03] px-4 py-3 text-sm font-medium tracking-[0.18em] text-white/46"
                  >
                    {capability}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section id="platform" className="px-4 py-8 sm:px-6 lg:px-8">
          <div className="section-shell grid items-center gap-8 rounded-[34px] border border-white/[0.08] bg-white/[0.03] p-6 backdrop-blur-sm sm:p-8 lg:grid-cols-[0.92fr_1.08fr]">
            <div className="max-w-2xl">
              <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
                Platform
              </p>
              <h2 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.7rem]">
                Complete URL-to-report coverage, driven by automated security checks
              </h2>
              <p className="mt-5 text-base leading-8 text-white/62">
                ScannerX is purpose-built around the real application you already
                have: target onboarding, asynchronous execution, live findings,
                AI-assisted finding enrichment, comparison views, report exports,
                and recurring rescans for the same URL.
              </p>

              <div className="mt-7 space-y-3">
                {platformHighlights.map((highlight) => (
                  <div key={highlight} className="flex items-start gap-3">
                    <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-cyan-300" />
                    <p className="text-sm leading-7 text-white/66">{highlight}</p>
                  </div>
                ))}
              </div>
            </div>

            <LandingVisual variant="overview" />
          </div>
        </section>

        <section id="use-cases" className="px-4 py-16 sm:px-6 lg:px-8">
          <div className="section-shell">
            <div className="max-w-3xl">
              <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
                Customer Outcomes
              </p>
              <h2 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.7rem]">
                Why teams trust ScannerX for day-to-day URL scanning
              </h2>
              <p className="mt-5 text-base leading-8 text-white/60">
                The public experience now mirrors the real operator flow in the
                project instead of generic security copy: define the target, prove
                ownership, run the scan, review what changed, and repeat whenever
                the URL needs another pass.
              </p>
            </div>

            <div className="mt-12 space-y-6">
              {outcomeRows.map((row, index) => (
                <OutcomeRow key={row.title} row={row} reverse={index % 2 === 1} />
              ))}
            </div>
          </div>
        </section>

        <section id="learn" className="px-4 py-8 sm:px-6 lg:px-8">
          <div className="section-shell rounded-[34px] border border-white/[0.08] bg-[#0b0d0e]/92 p-8">
            <div className="max-w-3xl">
              <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
                Learn
              </p>
              <h2 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.7rem]">
                From target onboarding to live findings review
              </h2>
              <p className="mt-5 text-base leading-8 text-white/60">
                After tracing the source, the workflow is clear and the landing copy
                now follows it closely: authenticate, create the target, queue the
                run, watch live findings, then compare, export, and schedule the
                next scan.
              </p>
            </div>

            <div className="mt-10 grid gap-4 lg:grid-cols-3">
              {learnCards.map((card) => (
                <article
                  key={card.title}
                  className="rounded-[28px] border border-white/[0.08] bg-white/[0.03] p-6"
                >
                  <p className="text-xs font-semibold uppercase tracking-[0.24em] text-cyan-300/82">
                    {card.label}
                  </p>
                  <h3 className="mt-5 text-2xl font-medium tracking-[-0.04em] text-white">
                    {card.title}
                  </h3>
                  <p className="mt-4 text-sm leading-7 text-white/60">{card.body}</p>
                  <a
                    href={card.href}
                    className="mt-7 inline-flex items-center gap-2 text-sm font-medium text-cyan-200 transition-colors hover:text-white"
                  >
                    <span>{card.linkLabel}</span>
                    <ArrowRight size={15} />
                  </a>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="pricing" className="px-4 py-16 sm:px-6 lg:px-8">
          <div className="section-shell rounded-[34px] border border-white/[0.08] bg-gradient-to-r from-white/[0.04] to-cyan-300/[0.05] p-8">
            <div className="max-w-3xl">
              <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
                Pricing
              </p>
              <h2 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.7rem]">
                Everything you need once the scan is complete
              </h2>
              <p className="mt-5 text-base leading-8 text-white/60">
                Instead of placeholder marketing, this section now points at the
                outputs already implemented in the project: live progress,
                AI-enriched findings, reports, comparison, notifications, and
                scheduled rescans for the same target.
              </p>
            </div>

            <div className="mt-10 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {productStats.map((card) => (
                <div
                  key={card.label}
                  className="rounded-[28px] border border-white/[0.08] bg-[#0b0d0e]/88 p-6"
                >
                  <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
                    {card.label}
                  </p>
                  <p className="mt-3 text-4xl font-medium tracking-[-0.05em] text-white">
                    {card.value}
                  </p>
                  <p className="mt-3 text-sm leading-6 text-white/58">{card.description}</p>
                </div>
              ))}
            </div>

            <div className="mt-10 grid gap-4 lg:grid-cols-4">
              {workflowCards.map((card) => (
                <div
                  key={card.title}
                  className="rounded-[26px] border border-white/[0.08] bg-[#0b0d0e]/88 p-6"
                >
                  <h3 className="text-lg font-medium text-white">{card.title}</h3>
                  <p className="mt-3 text-sm leading-7 text-white/60">{card.body}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="docs" className="px-4 pb-12 pt-8 sm:px-6 lg:px-8">
          <div className="section-shell">
            <div className="relative overflow-hidden rounded-[34px] border border-white/[0.08] bg-[#0b0d0e]/94 p-8 text-center sm:p-10">
              <div className="pointer-events-none absolute inset-0 overflow-hidden opacity-70">
                <img
                  src={heroGlowImages.left}
                  alt=""
                  className="absolute left-[-12%] top-[-20%] w-[42rem] max-w-none"
                />
                <img
                  src={heroGlowImages.right}
                  alt=""
                  className="absolute right-[-12%] top-[-20%] w-[42rem] max-w-none"
                />
              </div>

              <div className="relative z-10">
                <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
                  Docs
                </p>
                <h2 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.7rem]">
                  Enter ScannerX and start with a URL target
                </h2>
                <p className="mx-auto mt-5 max-w-3xl text-base leading-8 text-white/60">
                  Sign in to manage targets, queue a new scan, watch findings
                  arrive, compare scan runs, export reports, and keep the same URL
                  under recurring watch without changing the backend behavior you
                  already built.
                </p>

                <div className="mt-8 flex flex-col items-center justify-center gap-4 sm:flex-row">
                  <Link
                    to={authHref}
                    className="rounded-[15px] border border-white/[0.1] bg-white px-6 py-3 text-base font-medium text-black transition-transform hover:-translate-y-0.5"
                  >
                    {authLabel}
                  </Link>
                  <Link
                    to={primaryHref}
                    className="rounded-[15px] border border-cyan-300/20 bg-cyan-300/8 px-6 py-3 text-base font-medium text-cyan-200 transition-transform hover:-translate-y-0.5"
                  >
                    {primaryLabel}
                  </Link>
                </div>
              </div>
            </div>

            <footer className="mt-6 rounded-[34px] border border-white/[0.08] bg-[#0b0d0e]/94 p-8">
              <div className="grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
                <div className="max-w-md">
                  <LandingScannerXWordmark className="h-auto w-[176px]" />
                  <p className="mt-5 text-base leading-8 text-white/60">
                    Join teams using ScannerX to add a target, run a scan,
                    review AI-enriched findings, export results, and rescan
                    important URLs on a schedule.
                  </p>
                </div>

                <div className="grid gap-8 sm:grid-cols-3">
                  {footerColumns.map((column) => (
                    <div key={column.title}>
                      <p className="text-base font-medium text-white">{column.title}</p>
                      <div className="mt-4 space-y-3">
                        {column.links.map((item) => (
                          <FooterNavLink key={item.label} item={item} />
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-8 flex flex-col gap-3 border-t border-white/[0.08] pt-6 text-sm text-white/48 sm:flex-row sm:items-center sm:justify-between">
                <p>© 2026 ScannerX. All rights reserved.</p>
                <div className="flex flex-wrap items-center gap-4">
                  <a href="#platform" className="transition-colors hover:text-white/72">
                    Platform
                  </a>
                  <a href="#use-cases" className="transition-colors hover:text-white/72">
                    Workflow
                  </a>
                  <a href="#pricing" className="transition-colors hover:text-white/72">
                    Outputs
                  </a>
                </div>
              </div>
            </footer>
          </div>
        </section>
      </main>
    </div>
  )
}

function LandingScannerXWordmark({ className = '' }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 700 120"
      fill="none"
      className={className}
      aria-label="SCANNERX logo"
      role="img"
      preserveAspectRatio="xMidYMid meet"
    >
      <text
        fill="currentColor"
        fontFamily="Manrope, Inter, system-ui, sans-serif"
        fontStyle="italic"
        fontWeight="800"
        letterSpacing="-10"
        textAnchor="middle"
        x="300"
        fontSize="110"
        y="100"
      >
        SCANNERX
      </text>
    </svg>
  )
}

function OutcomeRow({ row, reverse }) {
  return (
    <div
      className={`grid items-center gap-6 rounded-[34px] border border-white/[0.08] bg-white/[0.025] p-6 sm:p-8 lg:grid-cols-[0.9fr_1.1fr] ${
        reverse ? 'lg:[&>*:first-child]:order-2 lg:[&>*:last-child]:order-1' : ''
      }`}
    >
      <div className="max-w-2xl">
        <p className="text-xs font-semibold uppercase tracking-[0.28em] text-cyan-300/82">
          {row.eyebrow}
        </p>
        <h3 className="mt-4 text-3xl font-medium tracking-[-0.045em] text-white sm:text-[2.25rem]">
          {row.title}
        </h3>
        <p className="mt-5 text-base leading-8 text-white/60">{row.body}</p>

        <div className="mt-7 space-y-3">
          {row.bullets.map((bullet) => (
            <div key={bullet} className="flex items-start gap-3">
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-cyan-300" />
              <p className="text-sm leading-7 text-white/64">{bullet}</p>
            </div>
          ))}
        </div>
      </div>

      <LandingVisual variant={row.visual} />
    </div>
  )
}

function LandingVisual({ variant }) {
  if (variant === 'target') {
    return (
      <div className="rounded-[32px] border border-white/[0.08] bg-[#090b0d] p-4 shadow-[0_28px_70px_rgba(0,0,0,0.35)]">
        <div className="rounded-[26px] border border-white/[0.07] bg-[#0f1214] p-4">
          <div className="flex items-center justify-between border-b border-white/[0.07] pb-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-zinc-500">
                Target Setup
              </p>
              <p className="mt-2 text-lg font-medium text-white">scannerx.app/login</p>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200">
              <Target size={18} />
            </div>
          </div>

          <div className="mt-4 space-y-3">
            <VisualField label="Base URL" value="https://scannerx.app/login" />
            <VisualField label="Project Group" value="Production URLs" />
            <VisualField label="Tags" value="auth, public, release-2026" />
          </div>

          <div className="mt-4 rounded-[22px] border border-cyan-300/14 bg-cyan-300/[0.06] p-4">
            <div className="flex items-start gap-3">
              <ShieldCheck className="mt-1 h-5 w-5 shrink-0 text-cyan-200" />
              <div>
                <p className="text-sm font-medium text-white">Ready to scan immediately</p>
                <p className="mt-2 text-xs text-cyan-100/90">
                  Save the URL and move straight into queued execution.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (variant === 'scan') {
    return (
      <div className="rounded-[32px] border border-white/[0.08] bg-[#090b0d] p-4 shadow-[0_28px_70px_rgba(0,0,0,0.35)]">
        <div className="rounded-[26px] border border-white/[0.07] bg-[#0f1214] p-4">
          <div className="flex items-center justify-between border-b border-white/[0.07] pb-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-zinc-500">
                Scan Queue
              </p>
              <p className="mt-2 text-lg font-medium text-white">Queued execution</p>
            </div>
            <div className="rounded-full border border-emerald-300/25 bg-emerald-300/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-emerald-200">
              Live
            </div>
          </div>

          <div className="mt-4 rounded-full bg-white/[0.06] p-1">
            <div className="h-2 w-[68%] rounded-full bg-gradient-to-r from-[#60e0ec] to-[#2de0c7]" />
          </div>

          <div className="mt-5 space-y-3">
            {[
              ['Profiling', 'Completed'],
              ['Discovery', 'Running'],
              ['Security Checks', 'Running'],
              ['XSS Review', 'Queued'],
              ['Injection Checks', 'Queued'],
            ].map(([stage, status]) => (
              <div
                key={stage}
                className="flex items-center justify-between rounded-[20px] border border-white/[0.06] bg-white/[0.03] px-4 py-3"
              >
                <span className="text-sm font-medium text-white/90">{stage}</span>
                <span className="text-xs uppercase tracking-[0.2em] text-white/42">{status}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (variant === 'findings') {
    return (
      <div className="rounded-[32px] border border-white/[0.08] bg-[#090b0d] p-4 shadow-[0_28px_70px_rgba(0,0,0,0.35)]">
        <div className="rounded-[26px] border border-white/[0.07] bg-[#0f1214] p-4">
          <div className="flex items-center justify-between border-b border-white/[0.07] pb-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-zinc-500">
                Findings Feed
              </p>
              <p className="mt-2 text-lg font-medium text-white">Live triage stream</p>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200">
              <Activity size={18} />
            </div>
          </div>

          <div className="mt-4 space-y-3">
            <FindingRow title="Reflected XSS in returnUrl parameter" severity="High" />
            <FindingRow title="Directory listing exposed on /backup/" severity="Medium" />
            <FindingRow title="Outdated server banner identified" severity="Low" />
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <MiniStatusCard icon={BellRing} label="Notifications" value="Unread 3" />
            <MiniStatusCard icon={Search} label="Comparison" value="New 5" />
          </div>
        </div>
      </div>
    )
  }

  if (variant === 'reports') {
    return (
      <div className="rounded-[32px] border border-white/[0.08] bg-[#090b0d] p-4 shadow-[0_28px_70px_rgba(0,0,0,0.35)]">
        <div className="rounded-[26px] border border-white/[0.07] bg-[#0f1214] p-4">
          <div className="flex items-center justify-between border-b border-white/[0.07] pb-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-zinc-500">
                Outputs
              </p>
              <p className="mt-2 text-lg font-medium text-white">Reports and schedules</p>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200">
              <Download size={18} />
            </div>
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {[
              ['Executive HTML', 'Ready'],
              ['Detailed HTML', 'Ready'],
              ['CSV Export', 'Ready'],
              ['JSON Export', 'Ready'],
            ].map(([title, state]) => (
              <div
                key={title}
                className="rounded-[20px] border border-white/[0.06] bg-white/[0.03] px-4 py-4"
              >
                <p className="text-sm font-medium text-white">{title}</p>
                <p className="mt-2 text-xs uppercase tracking-[0.22em] text-cyan-200/80">
                  {state}
                </p>
              </div>
            ))}
          </div>

          <div className="mt-4 rounded-[22px] border border-white/[0.06] bg-white/[0.03] p-4">
            <div className="flex items-start gap-3">
              <CalendarClock className="mt-1 h-5 w-5 shrink-0 text-cyan-200" />
              <div>
                <p className="text-sm font-medium text-white">Recurring scan schedule</p>
                <p className="mt-2 font-mono text-xs text-white/58">0 4 * * 1-5</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="rounded-[32px] border border-white/[0.08] bg-[#090b0d] p-4 shadow-[0_28px_70px_rgba(0,0,0,0.35)]">
      <div className="rounded-[26px] border border-white/[0.07] bg-[#0f1214] p-4">
        <div className="flex items-center justify-between border-b border-white/[0.07] pb-4">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-zinc-500">
              ScannerX Workspace
            </p>
            <p className="mt-2 text-lg font-medium text-white">Targets, scans, findings, reports</p>
          </div>
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200">
            <Sparkles size={18} />
          </div>
        </div>

        <div className="mt-4 grid gap-4 xl:grid-cols-[0.36fr_0.64fr]">
          <div className="rounded-[22px] border border-white/[0.06] bg-white/[0.03] p-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
              Navigation
            </p>
            <div className="mt-4 space-y-2">
              {['Dashboard', 'Targets', 'Scans', 'Findings', 'Schedules'].map((item, index) => (
                <div
                  key={item}
                  className={`rounded-[16px] px-3 py-2 text-sm ${
                    index === 2
                      ? 'bg-cyan-300/12 text-cyan-100'
                      : 'bg-white/[0.02] text-white/58'
                  }`}
                >
                  {item}
                </div>
              ))}
            </div>
          </div>

          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-3">
              <MiniStatusCard icon={Target} label="Targets" value="24" />
              <MiniStatusCard icon={Radar} label="Active Scans" value="3" />
              <MiniStatusCard icon={Activity} label="Open Findings" value="17" />
            </div>

            <div className="rounded-[22px] border border-white/[0.06] bg-white/[0.03] p-4">
              <div className="flex items-center justify-between">
                <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
                  Recent scan
                </p>
                <span className="rounded-full bg-emerald-300/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.24em] text-emerald-200">
                  Running
                </span>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <VisualField label="Target" value="scannerx.app/login" compact />
                <VisualField label="Profile" value="Full Web Scan" compact />
                <VisualField label="Progress" value="68% complete" compact />
                <VisualField label="Queue" value="RabbitMQ active" compact />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function VisualField({ label, value, compact = false }) {
  return (
    <div
      className={`rounded-[18px] border border-white/[0.06] bg-white/[0.03] px-4 ${
        compact ? 'py-3' : 'py-3.5'
      }`}
    >
      <p className="text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
        {label}
      </p>
      <p className="mt-2 text-sm text-white/86">{value}</p>
    </div>
  )
}

function FindingRow({ title, severity }) {
  const tone =
    severity === 'High'
      ? 'bg-rose-400/12 text-rose-200'
      : severity === 'Medium'
        ? 'bg-amber-300/12 text-amber-100'
        : 'bg-cyan-300/10 text-cyan-100'

  return (
    <div className="rounded-[20px] border border-white/[0.06] bg-white/[0.03] px-4 py-4">
      <div className="flex items-start justify-between gap-4">
        <p className="text-sm leading-6 text-white/86">{title}</p>
        <span className={`rounded-full px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.22em] ${tone}`}>
          {severity}
        </span>
      </div>
    </div>
  )
}

function MiniStatusCard({ icon, label, value }) {
  const Icon = icon

  return (
    <div className="rounded-[20px] border border-white/[0.06] bg-white/[0.03] px-4 py-4">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-cyan-300/10 text-cyan-200">
          <Icon size={16} />
        </div>
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
            {label}
          </p>
          <p className="mt-1 text-sm text-white/86">{value}</p>
        </div>
      </div>
    </div>
  )
}

function MetricOrbitIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className="text-white/88"
    >
      <circle cx="10" cy="10" r="2.2" fill="currentColor" />
      <path
        d="M10 2.7c3.9 0 7 3.1 7 7s-3.1 7-7 7-7-3.1-7-7 3.1-7 7-7Z"
        stroke="currentColor"
        strokeOpacity="0.38"
        strokeWidth="1.4"
      />
      <path
        d="M3.9 6.3c1.4 1 3.6 1.6 6.1 1.6s4.7-.6 6.1-1.6"
        stroke="currentColor"
        strokeOpacity="0.7"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
      <path
        d="M3.9 13.7c1.4-1 3.6-1.6 6.1-1.6s4.7.6 6.1 1.6"
        stroke="currentColor"
        strokeOpacity="0.7"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

function FooterNavLink({ item }) {
  if (item.to) {
    return (
      <Link to={item.to} className="block text-sm text-white/58 transition-colors hover:text-white/82">
        {item.label}
      </Link>
    )
  }

  return (
    <a href={item.href} className="block text-sm text-white/58 transition-colors hover:text-white/82">
      {item.label}
    </a>
  )
}
