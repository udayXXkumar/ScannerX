import re

with open('frontend/src/pages/app/ScanDetail.jsx', 'r') as f:
    content = f.read()

content = content.replace(
    "import { AlertTriangle, Calendar, Download, PauseCircle, Play, Target, Trash2 } from 'lucide-react'",
    "import { AlertTriangle, Calendar, Download, PauseCircle, Play, Target, Trash2, ChevronDown, Check } from 'lucide-react'"
)

# State toggles
content = content.replace(
    "const [activeTab, setActiveTab] = useState('findings')",
    "const [activeTab, setActiveTab] = useState('findings')\n  const [showAiEnhanced, setShowAiEnhanced] = useState(true)\n  const [groupDuplicates, setGroupDuplicates] = useState(true)\n  const [showAiMenu, setShowAiMenu] = useState(false)"
)

# Findings processing
old_findings = """  const findings = useMemo(() => {
    const liveFindings = events
      .filter((event) => event.type === 'FINDING_FOUND' && event.data)
      .map((event) => event.data)

    const persistedFindings = Array.isArray(reportData?.findings) ? [...reportData.findings].reverse() : []

    const findingsByKey = new Map()

    ;[...persistedFindings, ...liveFindings].forEach((finding, index) => {
      if (!finding) {
        return
      }

      const sanitizedFinding = {
        ...finding,
        severity: normalizeFindingSeverity(finding.severity),
        title: sanitizeFindingTitle(finding.title),
        description: sanitizeFindingDescription(finding.description),
      }

      const key =
        finding.id ??
        `${sanitizedFinding.title || 'finding'}-${finding.affectedUrl || finding.endpoint || 'url'}-${finding.createdAt || index}`

      findingsByKey.set(key, sanitizedFinding)
    })

    return Array.from(findingsByKey.values())
  }, [events, reportData])"""

new_findings = """  const findings = useMemo(() => {
    const liveFindings = events
      .filter((event) => event.type === 'FINDING_FOUND' && event.data)
      .map((event) => event.data)

    const persistedFindings = Array.isArray(reportData?.findings) ? [...reportData.findings].reverse() : []

    const findingsByKey = new Map()

    ;[...persistedFindings, ...liveFindings].forEach((finding, index) => {
      if (!finding) {
        return
      }

      const sanitizedFinding = {
        ...finding,
        severity: normalizeFindingSeverity(finding.severity),
        title: sanitizeFindingTitle(finding.title),
        description: sanitizeFindingDescription(finding.description),
      }

      const key =
        finding.id ??
        `${sanitizedFinding.title || 'finding'}-${finding.affectedUrl || finding.endpoint || 'url'}-${finding.createdAt || index}`

      findingsByKey.set(key, sanitizedFinding)
    })

    let results = Array.from(findingsByKey.values())
    if (groupDuplicates) {
      results = results.filter(f => !f.aiDuplicateOfId && !f.duplicate)
    }
    return results
  }, [events, reportData, groupDuplicates])"""

content = content.replace(old_findings, new_findings)

# UI Dropdown
old_ui = """            <h2 className="flex items-center text-lg font-semibold text-white">
              <span className="mr-3 flex h-6 w-6 items-center justify-center rounded bg-prowler-green/10 text-xs font-bold text-prowler-green">
                {findings.length}
              </span>
              {findings.length} Discovered
            </h2>"""

new_ui = """            <h2 className="flex items-center text-lg font-semibold text-white">
              <span className="mr-3 flex h-6 w-6 items-center justify-center rounded bg-prowler-green/10 text-xs font-bold text-prowler-green">
                {findings.length}
              </span>
              {findings.length} Discovered
            </h2>
            
            <div className="relative ml-auto">
              <button 
                onClick={() => setShowAiMenu(!showAiMenu)}
                className="inline-flex items-center gap-2 rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm font-medium text-slate-300 transition-colors hover:bg-white/10 focus:outline-none"
              >
                AI <ChevronDown size={16} className={`transition-transform ${showAiMenu ? 'rotate-180' : ''}`} />
              </button>
              {showAiMenu && (
                <div className="absolute right-0 top-full z-50 mt-2 w-56 rounded-md border border-white/10 bg-slate-900 py-1 shadow-lg backdrop-blur-xl">
                  <button
                    onClick={() => setShowAiEnhanced(!showAiEnhanced)}
                    className="flex w-full items-center justify-between px-4 py-2 text-left text-sm text-slate-300 hover:bg-white/5"
                  >
                    <span>AI-Enhanced View</span>
                    {showAiEnhanced && <Check size={16} className="text-prowler-green" />}
                  </button>
                  <button
                    onClick={() => setGroupDuplicates(!groupDuplicates)}
                    className="flex w-full items-center justify-between px-4 py-2 text-left text-sm text-slate-300 hover:bg-white/5"
                  >
                    <span>Group Duplicates</span>
                    {groupDuplicates && <Check size={16} className="text-prowler-green" />}
                  </button>
                </div>
              )}
            </div>"""

content = content.replace(old_ui, new_ui)

# Update SeverityBadge inside the map
old_tr = """                  <div className="flex shrink-0 items-start gap-3">
                    <SeverityBadge severity={finding.severity} />
                  </div>"""

new_tr = """                  <div className="flex shrink-0 flex-col gap-2">
                    <SeverityBadge severity={showAiEnhanced && finding.aiSeverity ? finding.aiSeverity : finding.severity} />
                    {showAiEnhanced && finding.aiPriorityScore ? <span className="text-xs font-mono bg-indigo-500/20 text-indigo-300 px-1.5 py-0.5 rounded text-center">P{finding.aiPriorityScore}</span> : null}
                    {(finding.aiDuplicateOfId || finding.duplicate) ? <span className="text-xs font-mono bg-slate-500/20 text-slate-400 px-1.5 py-0.5 rounded text-center">DUP</span> : null}
                  </div>"""

content = content.replace(old_tr, new_tr)

# Title flex update
old_title_flex = """            <div className="mb-4 flex items-center justify-between border-b border-white/10 pb-4">"""
new_title_flex = """            <div className="mb-4 flex items-center border-b border-white/10 pb-4">"""
content = content.replace(old_title_flex, new_title_flex)

with open('frontend/src/pages/app/ScanDetail.jsx', 'w') as f:
    f.write(content)

