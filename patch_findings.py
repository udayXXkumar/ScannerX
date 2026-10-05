import re

with open('frontend/src/pages/app/FindingsList.jsx', 'r') as f:
    content = f.read()

content = content.replace(
    "import { AlertTriangle, Search, X } from 'lucide-react'",
    "import { AlertTriangle, Search, X, ChevronDown, Check } from 'lucide-react'"
)

# Add state variables
content = content.replace(
    "const [searchQuery, setSearchQuery] = useState('')",
    "const [searchQuery, setSearchQuery] = useState('')\n  const [showAiEnhanced, setShowAiEnhanced] = useState(true)\n  const [groupDuplicates, setGroupDuplicates] = useState(true)\n  const [showAiMenu, setShowAiMenu] = useState(false)"
)

# Replace filteredFindings logic
old_filtered = """  const filteredFindings = mergedFindings.filter((finding) => {
    if (filterSeverity !== 'All' && String(finding.severity).toUpperCase() !== filterSeverity) return false
    if (filterStatus !== 'All' && normalizeFindingStatus(finding.status) !== filterStatus) return false
    if (searchQuery) {
      const searchLower = searchQuery.toLowerCase()
      return (
        finding.title?.toLowerCase().includes(searchLower) ||
        finding.affectedUrl?.toLowerCase().includes(searchLower) ||
        finding.id?.toString().includes(searchLower)
      )
    }
    return true
  })"""

new_filtered = """  const filteredFindings = useMemo(() => {
    let filtered = mergedFindings.filter((finding) => {
      const displaySeverity = showAiEnhanced && finding.aiSeverity ? finding.aiSeverity : finding.severity
      if (filterSeverity !== 'All' && String(displaySeverity).toUpperCase() !== filterSeverity) return false
      if (filterStatus !== 'All' && normalizeFindingStatus(finding.status) !== filterStatus) return false
      if (searchQuery) {
        const searchLower = searchQuery.toLowerCase()
        return (
          finding.title?.toLowerCase().includes(searchLower) ||
          finding.affectedUrl?.toLowerCase().includes(searchLower) ||
          finding.id?.toString().includes(searchLower)
        )
      }
      return true
    })
    if (groupDuplicates) {
      filtered = filtered.filter(f => !f.aiDuplicateOfId)
    }
    return filtered
  }, [mergedFindings, filterSeverity, filterStatus, searchQuery, showAiEnhanced, groupDuplicates])"""

content = content.replace(old_filtered, new_filtered)

# Replace counts
content = content.replace(
    "const criticalCount = mergedFindings.filter((f) => String(f.severity).toUpperCase() === 'CRITICAL').length",
    "const criticalCount = mergedFindings.filter((f) => String(showAiEnhanced && f.aiSeverity ? f.aiSeverity : f.severity).toUpperCase() === 'CRITICAL').length"
)
content = content.replace(
    "const highCount = mergedFindings.filter((f) => String(f.severity).toUpperCase() === 'HIGH').length",
    "const highCount = mergedFindings.filter((f) => String(showAiEnhanced && f.aiSeverity ? f.aiSeverity : f.severity).toUpperCase() === 'HIGH').length"
)
content = content.replace(
    "const mediumCount = mergedFindings.filter((f) => String(f.severity).toUpperCase() === 'MEDIUM').length",
    "const mediumCount = mergedFindings.filter((f) => String(showAiEnhanced && f.aiSeverity ? f.aiSeverity : f.severity).toUpperCase() === 'MEDIUM').length"
)

# Replace UI Dropdown
old_ui = """          <div className="flex flex-1 items-center gap-4">
            <div className="relative max-w-md flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="text"
                placeholder="Search findings..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="modal-input w-full pl-10"
              />
            </div>"""

new_ui = """          <div className="flex flex-1 items-center gap-4">
            <div className="relative max-w-md flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="text"
                placeholder="Search findings..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="modal-input w-full pl-10"
              />
            </div>
            
            <div className="relative">
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
old_tr = """                    <td className="px-6 py-4">
                      <SeverityBadge severity={finding.severity} />
                    </td>"""

new_tr = """                    <td className="px-6 py-4 flex items-center gap-2">
                      <SeverityBadge severity={showAiEnhanced && finding.aiSeverity ? finding.aiSeverity : finding.severity} />
                      {showAiEnhanced && finding.aiPriorityScore ? <span className="text-xs font-mono bg-indigo-500/20 text-indigo-300 px-1.5 py-0.5 rounded">P{finding.aiPriorityScore}</span> : null}
                      {finding.aiDuplicateOfId ? <span className="text-xs font-mono bg-slate-500/20 text-slate-400 px-1.5 py-0.5 rounded">DUP</span> : null}
                    </td>"""

content = content.replace(old_tr, new_tr)

# Update Modal props
content = content.replace("<FindingModal", "<FindingModal showAiEnhanced={showAiEnhanced}")

# Update Modal component declaration
content = content.replace("const FindingModal = ({ finding, updateForm, setUpdateForm, onSubmit, onClose, isLoading }) => {", "const FindingModal = ({ finding, updateForm, setUpdateForm, onSubmit, onClose, isLoading, showAiEnhanced }) => {")

# Update Modal SeverityBadge
content = content.replace(
    "<SeverityBadge severity={finding.severity} />",
    """<SeverityBadge severity={showAiEnhanced && finding.aiSeverity ? finding.aiSeverity : finding.severity} />
                {showAiEnhanced && finding.aiPriorityScore ? <span className="text-sm font-mono bg-indigo-500/20 text-indigo-300 px-2 py-1 rounded border border-indigo-500/30">Priority {finding.aiPriorityScore}/10</span> : null}"""
)

# Update Modal Description
content = content.replace(
    "{getFindingDisplayDescription(finding) || 'No description available.'}",
    "{showAiEnhanced && getFindingDisplayDescription(finding) ? getFindingDisplayDescription(finding) : finding.description || 'No description available.'}"
)

# Update Modal Exploit Narrative check
content = content.replace(
    "{getFindingExploitNarrative(finding) ? (",
    "{showAiEnhanced && getFindingExploitNarrative(finding) ? ("
)

with open('frontend/src/pages/app/FindingsList.jsx', 'w') as f:
    f.write(content)
