// Generates the sample question figures (SVG) used by the dev seed V1002.
// Run: node frontend/scripts/generate-sample-figures.cjs
const fs = require('fs')
const path = require('path')
const OUT = path.join(__dirname, '..', 'public', 'samples')
fs.mkdirSync(OUT, { recursive: true })

const head = (w, h, title) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="${title}">
<title>${title}</title>
<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>
<rect width="${w}" height="${h}" fill="#ffffff"/>
<g font-family="Inter, Segoe UI, Arial, sans-serif" font-size="14" fill="#111827" stroke-linecap="round" stroke-linejoin="round">
`
const tail = '</g>\n</svg>\n'
const save = (name, w, h, title, body) => fs.writeFileSync(path.join(OUT, name), head(w, h, title) + body + tail)
const r = (n) => Math.round(n * 10) / 10
const rad = (d) => (d * Math.PI) / 180

// 1. Smooth incline at 30 degrees with a block
{
  const x0 = 30, y0 = 200, L = 300, h = L * Math.tan(rad(30))
  const bx = x0 + 0.55 * L, by = y0 - 0.55 * h
  save('incline.svg', 360, 230, 'Block on a smooth inclined plane at 30 degrees', `
<path d="M${x0},${y0} L${x0 + L},${y0} L${x0 + L},${r(y0 - h)} Z" fill="#e0e7ff" stroke="#1f2937" stroke-width="2"/>
<g transform="translate(${r(bx)},${r(by)}) rotate(-30)">
  <rect x="-26" y="-38" width="52" height="38" fill="#fbbf24" stroke="#1f2937" stroke-width="2"/>
  <text x="0" y="-14" text-anchor="middle" font-weight="600">m</text>
</g>
<path d="M${x0 + 48},${y0} A48,48 0 0,0 ${r(x0 + 48 * Math.cos(rad(30)))},${r(y0 - 48 * Math.sin(rad(30)))}" fill="none" stroke="#1f2937" stroke-width="1.5"/>
<text x="${x0 + 56}" y="${y0 - 8}">30°</text>
<text x="${x0 + L - 8}" y="${y0 + 20}" text-anchor="end" font-size="12" fill="#4b5563">smooth (frictionless) surface</text>
<rect x="${x0 + L - 12}" y="${y0 - 12}" width="12" height="12" fill="none" stroke="#1f2937" stroke-width="1.2"/>
`)
}

// 1b. Free-body diagram (solution figure)
{
  const cx = 180, cy = 120, t = Math.tan(rad(30))
  save('incline-fbd.svg', 360, 250, 'Free-body diagram of the block on the incline', `
<line x1="20" y1="${r(cy + 22 + 160 * t)}" x2="340" y2="${r(cy + 22 - 160 * t)}" stroke="#9ca3af" stroke-width="2"/>
<g transform="translate(${cx},${cy}) rotate(-30)">
  <rect x="-26" y="-19" width="52" height="38" fill="#fbbf24" stroke="#1f2937" stroke-width="2"/>
  <line x1="0" y1="0" x2="0" y2="-85" stroke="#2563eb" stroke-width="2.5" marker-end="url(#ah)"/>
  <line x1="0" y1="0" x2="-95" y2="0" stroke="#dc2626" stroke-width="2.5" marker-end="url(#ah)"/>
</g>
<line x1="${cx}" y1="${cy}" x2="${cx}" y2="${cy + 95}" stroke="#111827" stroke-width="2.5" marker-end="url(#ah)"/>
<text x="${cx + 8}" y="${cy + 92}" font-weight="600">mg</text>
<text x="128" y="50" text-anchor="end" fill="#2563eb" font-weight="600">N = mg cos θ</text>
<text x="36" y="152" fill="#dc2626" font-weight="600">mg sin θ</text>
<text x="20" y="24" font-size="13" fill="#4b5563">Along the incline: ma = mg sin θ, so a = g sin θ</text>
`)
}

// 2. Velocity-time graph
{
  const ox = 50, oy = 200, sx = 29, sy = 7
  const P = (t, v) => `${ox + t * sx},${oy - v * sy}`
  const tick = (t) => `<line x1="${ox + t * sx}" y1="${oy}" x2="${ox + t * sx}" y2="${oy + 5}" stroke="#1f2937"/><text x="${ox + t * sx}" y="${oy + 20}" text-anchor="middle">${t}</text>`
  save('vt-graph.svg', 380, 250, 'Velocity-time graph: 0 to 20 m/s in 4 s, constant until 8 s, back to 0 at 10 s', `
<line x1="${ox}" y1="${oy}" x2="${ox + 11 * sx}" y2="${oy}" stroke="#1f2937" stroke-width="1.8" marker-end="url(#ah)"/>
<line x1="${ox}" y1="${oy}" x2="${ox}" y2="${oy - 24 * sy}" stroke="#1f2937" stroke-width="1.8" marker-end="url(#ah)"/>
<polygon points="${P(0, 0)} ${P(4, 20)} ${P(8, 20)} ${P(10, 0)}" fill="#c7d2fe" fill-opacity="0.6"/>
<polyline points="${P(0, 0)} ${P(4, 20)} ${P(8, 20)} ${P(10, 0)}" fill="none" stroke="#4338ca" stroke-width="3"/>
<line x1="${ox + 4 * sx}" y1="${oy - 20 * sy}" x2="${ox + 4 * sx}" y2="${oy}" stroke="#6b7280" stroke-dasharray="4 4"/>
<line x1="${ox + 8 * sx}" y1="${oy - 20 * sy}" x2="${ox + 8 * sx}" y2="${oy}" stroke="#6b7280" stroke-dasharray="4 4"/>
<line x1="${ox}" y1="${oy - 20 * sy}" x2="${ox + 4 * sx}" y2="${oy - 20 * sy}" stroke="#6b7280" stroke-dasharray="4 4"/>
<text x="${ox - 8}" y="${oy - 20 * sy + 5}" text-anchor="end">20</text>
<text x="${ox - 8}" y="${oy + 5}" text-anchor="end">0</text>
${tick(4)}${tick(8)}${tick(10)}
<text x="${ox + 11 * sx}" y="${oy - 10}" text-anchor="end" font-style="italic">t (s)</text>
<text x="${ox + 6}" y="${oy - 23 * sy}" font-style="italic">v (m/s)</text>
`)
}

// 3. Projectile at 30 degrees
{
  const x0 = 30, y0 = 200, x1 = 350, cy = y0 - ((x1 - x0) / 2) * Math.tan(rad(30))
  const apexY = (y0 + 2 * cy + y0) / 4, mx = (x0 + x1) / 2
  save('projectile.svg', 380, 240, 'Projectile launched at 30 degrees with speed u; maximum height H and range R', `
<line x1="15" y1="${y0}" x2="365" y2="${y0}" stroke="#1f2937" stroke-width="1.8"/>
<path d="M${x0},${y0} Q${mx},${r(cy)} ${x1},${y0}" fill="none" stroke="#4338ca" stroke-width="2.5" stroke-dasharray="7 5"/>
<line x1="${x0}" y1="${y0}" x2="${r(x0 + 80 * Math.cos(rad(30)))}" y2="${r(y0 - 80 * Math.sin(rad(30)))}" stroke="#dc2626" stroke-width="2.5" marker-end="url(#ah)"/>
<text x="${r(x0 + 70 * Math.cos(rad(30)))}" y="${r(y0 - 80 * Math.sin(rad(30)) - 8)}" fill="#dc2626" font-weight="600">u = 20 m/s</text>
<path d="M${x0 + 42},${y0} A42,42 0 0,0 ${r(x0 + 42 * Math.cos(rad(30)))},${r(y0 - 42 * Math.sin(rad(30)))}" fill="none" stroke="#1f2937"/>
<text x="${x0 + 48}" y="${y0 - 6}" font-size="13">30°</text>
<line x1="${mx}" y1="${r(apexY)}" x2="${mx}" y2="${y0}" stroke="#059669" stroke-width="2" marker-start="url(#ah)" marker-end="url(#ah)"/>
<text x="${mx + 8}" y="${r((apexY + y0) / 2 + 5)}" fill="#059669" font-weight="700">H</text>
<line x1="${x0}" y1="${y0 + 22}" x2="${x1}" y2="${y0 + 22}" stroke="#6b7280" stroke-width="1.5" marker-start="url(#ah)" marker-end="url(#ah)"/>
<rect x="${mx - 12}" y="${y0 + 12}" width="24" height="20" fill="#ffffff"/>
<text x="${mx}" y="${y0 + 27}" text-anchor="middle" fill="#4b5563" font-weight="600">R</text>
<text x="20" y="24" font-size="13" fill="#4b5563">Take g = 10 m/s²</text>
`)
}

// 4. Molecular shapes (option images). Central atom A, bonded atoms X, lone pairs as lobes.
{
  const atom = (x, y, label, fill = '#ffffff', rr = 15) => `<circle cx="${x}" cy="${y}" r="${rr}" fill="${fill}" stroke="#1f2937" stroke-width="1.8"/><text x="${x}" y="${y + 5}" text-anchor="middle" font-weight="600" font-size="13">${label}</text>`
  const bond = (x1, y1, x2, y2, dash = false) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#1f2937" stroke-width="3"${dash ? ' stroke-dasharray="5 4"' : ''}/>`
  const wedge = (x1, y1, x2, y2) => {
    const a = Math.atan2(y2 - y1, x2 - x1) + Math.PI / 2, w = 6
    return `<polygon points="${x1},${y1} ${r(x2 + w * Math.cos(a))},${r(y2 + w * Math.sin(a))} ${r(x2 - w * Math.cos(a))},${r(y2 - w * Math.sin(a))}" fill="#1f2937"/>`
  }
  const lone = (x, y, ang) => `<g transform="translate(${x},${y}) rotate(${ang})"><ellipse cx="0" cy="-30" rx="13" ry="20" fill="#fde68a" stroke="#b45309" stroke-width="1.5"/><circle cx="-4" cy="-32" r="2.4" fill="#92400e"/><circle cx="4" cy="-32" r="2.4" fill="#92400e"/></g>`
  const cx = 90, cy = 74
  const card = (name, title, body, caption) =>
    save(name, 180, 150, title, body)
  const A = () => atom(cx, cy, 'A', '#bfdbfe', 17)
  {
    const pts = [270, 30, 150].map((d) => [r(cx + 52 * Math.cos(rad(d))), r(cy + 52 * Math.sin(rad(d)))])
    card('shape-planar.svg', 'Molecular geometry diagram',
      pts.map(([x, y]) => bond(cx, cy, x, y)).join('') + pts.map(([x, y]) => atom(x, y, 'X')).join('') + A(), 'no lone pair, angles 120°')
  }
  {
    const X = [[cx - 50, cy + 36], [cx + 50, cy + 36], [cx + 8, cy + 54]]
    card('shape-pyramidal.svg', 'Molecular geometry diagram',
      lone(cx, cy, 0) + bond(cx, cy, X[0][0], X[0][1]) + bond(cx, cy, X[1][0], X[1][1], true) + wedge(cx, cy, X[2][0], X[2][1]) +
      X.map(([x, y]) => atom(x, y, 'X')).join('') + A(), 'one lone pair, angle ≈ 107°')
  }
  {
    const X = [[cx, cy - 54], [cx - 52, cy + 32], [cx + 52, cy + 32], [cx + 10, cy + 54]]
    card('shape-tetrahedral.svg', 'Molecular geometry diagram',
      bond(cx, cy, X[0][0], X[0][1]) + bond(cx, cy, X[1][0], X[1][1]) + bond(cx, cy, X[2][0], X[2][1], true) + wedge(cx, cy, X[3][0], X[3][1]) +
      X.map(([x, y]) => atom(x, y, 'X')).join('') + A(), 'no lone pair, angles 109.5°')
  }
  {
    const X = [[cx - 48, cy + 40], [cx + 48, cy + 40]]
    card('shape-bent.svg', 'Molecular geometry diagram',
      lone(cx, cy, -38) + lone(cx, cy, 38) + X.map(([x, y]) => bond(cx, cy, x, y)).join('') + X.map(([x, y]) => atom(x, y, 'X')).join('') + A(),
      'two lone pairs, angle ≈ 104.5°')
  }
}

// 5. Hydrogen energy levels with a 3 -> 2 transition
{
  const lv = [[1, -13.6, 210], [2, -3.4, 110], [3, -1.51, 72], [4, -0.85, 52], ['∞', 0, 30]]
  const y2 = 110, y3 = 72
  save('energy-levels.svg', 360, 240, 'Energy levels of the hydrogen atom with an electron transition from n = 3 to n = 2', `
${lv.map(([n, e, y]) => `<line x1="70" y1="${y}" x2="260" y2="${y}" stroke="#1f2937" stroke-width="${n === '∞' ? 1.2 : 2}"${n === '∞' ? ' stroke-dasharray="6 4"' : ''}/>
<text x="60" y="${y + 5}" text-anchor="end" font-style="italic">n = ${n}</text>
<text x="268" y="${y + 5}" font-size="12" fill="#4b5563">${e === 0 ? '0 eV' : '−' + Math.abs(e).toFixed(2) + ' eV'}</text>`).join('\n')}
<line x1="170" y1="${y3}" x2="170" y2="${y2 - 2}" stroke="#dc2626" stroke-width="3" marker-end="url(#ah)"/>
<path d="M182,${(y3 + y2) / 2} q8,-8 16,0 t16,0 t16,0" fill="none" stroke="#dc2626" stroke-width="2"/>
<text x="236" y="${(y3 + y2) / 2 + 5}" fill="#dc2626" font-weight="700">?</text>
<text x="170" y="234" text-anchor="middle" font-size="12" fill="#4b5563">(not to scale)</text>
`)
}

// 6. Parabola y = x^2 + bx + c crossing the x-axis at 2 and 3
{
  const ox = 60, oy = 170, sx = 55, sy = 32
  const pts = []
  for (let x = 0.6; x <= 4.4001; x += 0.05) pts.push(`${r(ox + x * sx)},${r(oy - (x - 2) * (x - 3) * sy)}`)
  save('parabola.svg', 360, 240, 'Graph of y = x² + bx + c cutting the x-axis at x = 2 and x = 3', `
<line x1="20" y1="${oy}" x2="340" y2="${oy}" stroke="#1f2937" stroke-width="1.8" marker-end="url(#ah)"/>
<line x1="${ox}" y1="225" x2="${ox}" y2="15" stroke="#1f2937" stroke-width="1.8" marker-end="url(#ah)"/>
<text x="336" y="${oy + 20}" text-anchor="end" font-style="italic">x</text>
<text x="${ox + 8}" y="22" font-style="italic">y</text>
<text x="${ox - 8}" y="${oy + 18}" text-anchor="end">O</text>
<polyline points="${pts.join(' ')}" fill="none" stroke="#4338ca" stroke-width="3"/>
<circle cx="${ox + 2 * sx}" cy="${oy}" r="4.5" fill="#dc2626"/><circle cx="${ox + 3 * sx}" cy="${oy}" r="4.5" fill="#dc2626"/>
<text x="${ox + 2 * sx}" y="${oy + 22}" text-anchor="middle" font-weight="600">2</text>
<text x="${ox + 3 * sx}" y="${oy + 22}" text-anchor="middle" font-weight="600">3</text>
<text x="${ox + 3.3 * sx}" y="40" font-style="italic" fill="#4338ca">y = x² + bx + c</text>
`)
}
console.log(fs.readdirSync(OUT).join(' '))
