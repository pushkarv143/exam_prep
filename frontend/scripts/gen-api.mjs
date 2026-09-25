// Regenerates src/api/schema.d.ts from the backend's OpenAPI document.
// Usage: npm run gen:api   (VITE_PROXY_TARGET selects the backend, default http://localhost:8080)
import { execSync } from 'node:child_process'

const target = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080'
execSync(`npx openapi-typescript ${target}/v3/api-docs -o src/api/schema.d.ts`, { stdio: 'inherit' })
