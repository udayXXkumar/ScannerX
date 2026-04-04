export function normalizeFindingSeverity(severity) {
  const value = String(severity || 'LOW').trim().toUpperCase()

  switch (value) {
    case 'INFORMATIONAL':
    case 'INFORMATION':
      return 'INFO'
    case 'CRITICAL':
    case 'HIGH':
    case 'MEDIUM':
    case 'LOW':
    case 'INFO':
      return value
    default:
      return 'LOW'
  }
}

export function sanitizeFindingTitle(title) {
  const value = String(title || '').trim()
  if (!value) {
    return 'Security Result'
  }

  return value
    .replace(/^Nuclei Match:\s*/i, '')
    .replace(/^Discovered Path \((?:ffuf|Dirb|Gobuster)\):\s*/i, 'Discovered Path: ')
    .replace(/^Nikto Finding$/i, 'Security Check Result')
    .replace(/^Dalfox XSS:\s*/i, 'Potential Cross-Site Scripting: ')
    .replace(/^XSStrike XSS Match$/i, 'Potential Cross-Site Scripting')
    .replace(/^XSSer Injection Payload$/i, 'Confirmed Cross-Site Scripting')
    .replace(/^Arachni Detection$/i, 'Security Check Result')
}

export function sanitizeFindingDescription(description) {
  const value = String(description || '').trim()
  if (!value) {
    return ''
  }

  return value
    .replace(/\bFfuf discovered path\b/gi, 'Discovered a reachable path')
    .replace(/\bDirb found path\b/gi, 'Discovered a reachable path')
    .replace(/\bGobuster found directory\b/gi, 'Discovered a reachable path')
    .replace(/\bSqlmap detected an injection vector\b/gi, 'Detected an injection vector')
    .replace(/\bWapiti scan discovered potential vulnerability\b/gi, 'Detected a potential vulnerability')
    .replace(/\bXSStrike found a potential XSS vector\b/gi, 'Detected a potential cross-site scripting vector')
    .replace(/\bXSSer confirmed injection success\b/gi, 'Confirmed a cross-site scripting payload')
    .replace(/\bw3af discovered vulnerability payload\b/gi, 'Detected a vulnerability payload')
}

export function getFindingCategoryLabel(finding) {
  return String(finding?.category || '').trim() || 'Security Result'
}
