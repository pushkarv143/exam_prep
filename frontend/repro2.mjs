import puppeteer from 'puppeteer-core'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const BASE = 'http://localhost:5173'

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})
const page = await browser.newPage()

const log = []
page.on('response', async (res) => {
  const url = res.url()
  if (url.includes('/api/')) {
    let code = ''
    try { const j = await res.json(); code = j?.error?.code ?? (j?.success === true ? 'OK' : '') } catch {}
    log.push(`${res.status()} ${res.request().method()} ${url.replace(BASE, '')} code=${code}`)
  }
})
page.on('framenavigated', (f) => { if (f === page.mainFrame()) log.push('NAV-> ' + f.url().replace(BASE, '')) })

// Seed a STALE (structurally bogus / revoked-style) token, as if left over from earlier.
await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
await page.evaluate(() => {
  const stale = {
    state: {
      accessToken: 'eyJhbGciOiJIUzM4NCJ9.STALE.INVALID',
      refreshToken: 'eyJhbGciOiJIUzM4NCJ9.STALEREFRESH.INVALID',
      user: { id: '00000000-0000-7000-8000-000000000003', email: 'student@examprep.local', fullName: 'Demo Student', roles: ['STUDENT'], status: 'ACTIVE', emailVerified: true, createdAt: '2020-01-01T00:00:00Z' },
    },
    version: 0,
  }
  localStorage.setItem('examprep-auth', JSON.stringify(stale))
})

await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
await new Promise((r) => setTimeout(r, 1500))
log.push('=== after initial load with stale token, url=' + page.url().replace(BASE, ''))

// Try to log in if the form is present.
const hasForm = await page.$('#identifier')
log.push('login form present: ' + !!hasForm)
if (hasForm) {
  await page.type('#identifier', 'student@examprep.local')
  await page.type('#password', 'Student@123')
  await page.click('button[type=submit]')
  await new Promise((r) => setTimeout(r, 4000))
}
log.push('=== final url=' + page.url().replace(BASE, ''))
const after = await page.evaluate(() => localStorage.getItem('examprep-auth'))
log.push('token after: ' + (after ? JSON.parse(after).state.accessToken.slice(0, 25) : after))

console.log(log.join('\n'))
await browser.close()
