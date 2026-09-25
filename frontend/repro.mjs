import puppeteer from 'puppeteer-core'

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const BASE = 'http://localhost:5173'

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})

const page = await browser.newPage()

const netLog = []
page.on('response', async (res) => {
  const url = res.url()
  if (url.includes('/api/')) {
    let code = ''
    try { const j = await res.json(); code = j?.error?.code ?? (j?.success === true ? 'OK' : '') } catch {}
    netLog.push(`${res.status()} ${res.request().method()} ${url.replace(BASE, '')} code=${code}`)
  }
})
page.on('console', (m) => console.log('CONSOLE:', m.type(), m.text()))
page.on('pageerror', (e) => console.log('PAGEERROR:', e.message))

// Start clean.
await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })
await page.evaluate(() => { localStorage.clear() })
await page.goto(BASE + '/login', { waitUntil: 'networkidle0' })

console.log('URL before login:', page.url())

await page.type('#identifier', 'student@examprep.local')
await page.type('#password', 'Student@123')

const before = await page.evaluate(() => localStorage.getItem('examprep-auth'))
console.log('auth storage before:', before)

await Promise.all([
  page.click('button[type=submit]'),
])

// Wait for navigation / settle
await new Promise((r) => setTimeout(r, 4000))

console.log('URL after login:', page.url())
const after = await page.evaluate(() => localStorage.getItem('examprep-auth'))
console.log('auth storage after:', after ? after.slice(0, 120) + '...' : after)

console.log('--- NETWORK ---')
console.log(netLog.join('\n'))

await browser.close()
