const termsSections = [
  {
    title: 'Authorization and Scope',
    points: [
      'You must have explicit permission, contractual authority, or another valid legal basis before scanning, testing, or assessing any website, application, API, host, or related asset through ScannerX.',
      'You must stay within the scope, rate limits, maintenance windows, and restrictions set by the asset owner or authorized program.',
      'You must not scan third-party assets, protected systems, government systems, or critical infrastructure unless you are expressly authorized to do so.',
    ],
  },
  {
    title: 'Responsible Use',
    points: [
      'You are solely responsible for how ScannerX is used through your account, browser session, or device.',
      'You must not use ScannerX for unlawful, unauthorized, disruptive, destructive, deceptive, or privacy-invasive activity.',
      'You must not use ScannerX to attempt service disruption, denial-of-service behavior, credential misuse, data theft, unauthorized access, or exploitation beyond lawful authorized testing.',
    ],
  },
  {
    title: 'Target Owner Disclaimer',
    points: [
      'Websites, applications, asset owners, and operators that you choose to scan are not responsible for any misuse of ScannerX by you or by anyone acting through your account.',
      'If you misuse the platform, the responsibility remains with you, not with the website owner, its users, or other third parties connected to the scanned asset.',
    ],
  },
  {
    title: 'Operational Conditions',
    points: [
      'You must review results responsibly and avoid overstating findings before validation.',
      'You must respect applicable reporting obligations, internal policies, and any coordinated disclosure or incident-handling processes that apply to your engagement.',
      'You must stop scanning if authorization ends, scope changes, or you become aware that a target is outside your approved testing boundaries.',
    ],
  },
  {
    title: 'Legal Compliance',
    points: [
      'You are responsible for complying with applicable cybercrime, computer misuse, privacy, and data-protection laws in every jurisdiction connected to your activity.',
      'For India-focused use, this includes the Information Technology Act, 2000 and relevant CERT-In directions issued under Section 70B, along with any other applicable local requirements.',
      'If your activity touches systems, data, or operators in other jurisdictions, you must also comply with the computer misuse or cybercrime laws that apply there.',
    ],
  },
]

const referenceLinks = [
  {
    label: 'Information Technology Act, 2000',
    href: 'https://www.meity.gov.in/writereaddata/files/The%20Information%20Technology%20Act%2C%202000%283%29.pdf',
  },
  {
    label: 'CERT-In Directions under Section 70B',
    href: 'https://www.cert-in.org.in/Directions70B.jsp',
  },
  {
    label: 'Cross-jurisdiction computer misuse law context',
    href: 'https://www.justice.gov/jm/jm-9-48000-computer-fraud',
  },
]

export default function TermsPage() {
  return (
    <div className="w-full space-y-8">
      <div className="auth-heading mb-0 text-left sm:text-center">
        <p className="auth-eyebrow">Terms &amp; Conditions</p>
        <h1 className="auth-title">Use ScannerX responsibly and within authorized scope</h1>
        <p className="mt-4 text-sm leading-7 text-zinc-400 sm:text-base">
          These terms are a platform-use policy for ScannerX. They are intended to set expectations
          for lawful, authorized use and are not legal advice.
        </p>
      </div>

      <article className="space-y-6 text-sm leading-7 text-zinc-300 sm:text-[15px]">
        {termsSections.map((section) => (
          <section key={section.title} className="space-y-3 rounded-2xl border border-white/8 bg-white/[0.03] p-5 sm:p-6">
            <h2 className="text-lg font-semibold text-white">{section.title}</h2>
            <ul className="space-y-3 text-zinc-300">
              {section.points.map((point) => (
                <li key={point} className="flex items-start gap-3">
                  <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-[#60dfb2]" />
                  <span>{point}</span>
                </li>
              ))}
            </ul>
          </section>
        ))}

        <section className="space-y-3 rounded-2xl border border-white/8 bg-white/[0.03] p-5 sm:p-6">
          <h2 className="text-lg font-semibold text-white">Reference Links</h2>
          <p className="text-zinc-400">
            ScannerX provides these official references as a starting point only. You are
            responsible for checking the rules that apply to your specific environment and use case.
          </p>
          <ul className="space-y-2">
            {referenceLinks.map((reference) => (
              <li key={reference.href}>
                <a
                  href={reference.href}
                  target="_blank"
                  rel="noreferrer"
                  className="app-link font-medium"
                >
                  {reference.label}
                </a>
              </li>
            ))}
          </ul>
        </section>
      </article>
    </div>
  )
}
