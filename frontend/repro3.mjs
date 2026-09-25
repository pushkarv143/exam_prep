import puppeteer from 'puppeteer-core'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const BASE = 'http://localhost:5173'

const browser = await puppeteer.launch({
  executablePath: CHROME, headless: 'new',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const page = await browser.newPage()
const log = []
page.on('framenavigated', (f) => { if (f === page.mainFrame()) log.push('NAV-> ' + f.url().replace(BASE, '')) })
page.on('response', async (res) => {
  const u = res.url()
  if (u.includes('/api/v1/')) {
    let code = ''; try { const j = await res.json(); code = j?.error?.code ?? (j?.success ? 'OK' : '') } catch {}
    log.push(`  RESP ${res.status()} ${res.request().method()} ${u.replace(BASE, '')} code=${code}`)
  }
})

// Delay the stale dashboard GETs so they remain in-flight past the fresh login.
await page.setRequestInterception(true)
page.on('request', async (req) => {
  const u = req.url()
  if (u.includes('/api/v1/me/analytics') || u.includes('/api/v1/me/series')) {
    await new Promise((r) => setTimeout(r, 2500))
  }
  req.continue()
})

// Seed a stale/invalid token.
await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await page.evaluate(() => {
  localStorage.setItem('examprep-auth', JSON.stringify({
    state: {
      accessToken: 'eyJhbGciOiJIUzM4NCJ9.STALE.INVALID',
      refreshToken: 'eyJhbGciOiJIUzM4NCJ9.STALEREFRESH.INVALID',
      user: { id: '00000000-0000-7000-8000-000000000003', email: 'student@examprep.local', fullName: 'Demo Student', roles: ['STUDENT'], status: 'ACTIVE', emailVerified: true, createdAt: '2020-01-01T00:00:00Z' },
    }, version: 0,
  }))
})

// Load: GuestOnly redirects to /dashboard, firing the (now delayed) stale /me/* requests.
await page.goto(BASE + '/dashboard', { waitUntil: 'domcontentloaded' })
// Wait until the app bounces us to /login (stale token) OR the form appears; but do it FAST,
// before the delayed stale requests resolve.
await new Promise((r) => setTimeout(r, 300))
log.push('URL ~300ms after load: ' + page.url().replace(BASE, ''))

// If a form is present quickly, log in immediately (racing the in-flight stale requests).
let hasForm = await page.$('#identifier')
if (!hasForm) {
  // Force to login form fast.
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  hasForm = await page.$('#identifier')
}
log.push('form present: ' + !!hasForm)
if (hasForm) {
  await page.type('#identifier', 'student@examprep.local')
  await page.type('#password', 'Student@123')
  await page.click('button[type=submit]')
  log.push('--- submitted login ---')
}

await new Promise((r) => setTimeout(r, 5000))
log.push('FINAL url: ' + page.url().replace(BASE, ''))
const tok = await page.evaluate(() => { const v = localStorage.getItem('examprep-auth'); return v ? JSON.parse(v).state.accessToken : null })
log.push('FINAL token: ' + (tok ? tok.slice(0, 25) : tok))

console.log(log.join('\n'))
await browser.close()
