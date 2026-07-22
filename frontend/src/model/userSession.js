const pointsValue = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.trunc(parsed) : 0
}

export function createAnonymousSession() {
  return {
    authenticated: false,
    userId: null,
    email: '',
    points: 0,
  }
}

export function normalizeUserSession(value) {
  if (!value || typeof value !== 'object') return createAnonymousSession()

  const user = value.user && typeof value.user === 'object' ? value.user : value
  const email = typeof user.email === 'string' ? user.email.trim() : ''
  const explicitAuthentication = value.authenticated ?? value.loggedIn
  const authenticated = explicitAuthentication == null
    ? Boolean(email)
    : Boolean(explicitAuthentication)

  if (!authenticated) return createAnonymousSession()

  return {
    authenticated: true,
    userId: user.id ?? value.userId ?? null,
    email,
    points: pointsValue(value.points ?? user.points),
  }
}

export function maskEmail(email) {
  const [localPart = '', domain = ''] = String(email || '').split('@')
  if (!localPart || !domain) return '已登录用户'

  const visibleLocal = Array.from(localPart).slice(0, Math.min(2, localPart.length)).join('')
  const [domainName = '', ...suffixParts] = domain.split('.')
  const visibleDomain = domainName ? `${domainName[0]}***` : '***'
  const suffix = suffixParts.length ? `.${suffixParts.join('.')}` : ''
  return `${visibleLocal}***@${visibleDomain}${suffix}`
}
