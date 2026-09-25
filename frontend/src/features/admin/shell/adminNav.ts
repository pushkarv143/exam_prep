import {
  BarChart3, ClipboardCheck, Cpu, FileQuestion, Home, KeyRound, Library, ListChecks, ScrollText, ShieldCheck, Stamp,
  Users, type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** Shown when the user has ANY of these permissions (empty = everyone in the portal). */
  perms: string[]
  /** "g" + key jumps here. */
  shortcut?: string
  keywords?: string
  help: { summary: string; tips: string[] }
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

export const ADMIN_NAV: NavGroup[] = [
  {
    label: 'Overview',
    items: [
      {
        to: '/admin', label: 'Home', icon: Home, perms: [], shortcut: 'h', keywords: 'start onboarding checklist',
        help: {
          summary: 'Your starting point: a setup checklist and shortcuts to the areas you can use.',
          tips: ['The checklist ticks itself off as you complete each step.', 'Press Ctrl+K anywhere to jump to a page or search.'],
        },
      },
      {
        to: '/admin/dashboard', label: 'Dashboard', icon: BarChart3, perms: ['dashboard.view'], shortcut: 'd',
        keywords: 'kpi revenue attempts',
        help: { summary: 'Business KPIs: students, live attempts, revenue and published tests.', tips: ['Charts refresh every minute.'] },
      },
    ],
  },
  {
    label: 'Content',
    items: [
      {
        to: '/admin/questions', label: 'Question bank', icon: FileQuestion, perms: ['question.view'], shortcut: 'q',
        keywords: 'questions import latex',
        help: {
          summary: 'Search, create and bulk-import questions.',
          tips: ['Filters are kept in the URL, so you can bookmark or share a filtered list.',
            'Every save is a new version; tests keep the version they were built with.',
            'Teachers see and edit only the subjects assigned to them.', 'Archiving keeps the question in existing tests.'],
        },
      },
      {
        to: '/admin/review', label: 'Review queue', icon: ClipboardCheck,
        perms: ['question.review', 'question.approve', 'question.publish', 'question.create'], shortcut: 'w',
        keywords: 'review approve publish sla changes requested drafts',
        help: {
          summary: 'Questions waiting for review, sent back for changes, or approved and ready to publish.',
          tips: ['Reviews have a due time (SLA); overdue ones are highlighted and reminded by e-mail.',
            'Nobody approves a question they submitted or last edited.',
            'Tick rows to approve, publish or submit many questions at once.'],
        },
      },
      {
        to: '/admin/tests', label: 'Tests', icon: ListChecks, perms: ['test.view'], shortcut: 't', keywords: 'builder papers publish',
        help: {
          summary: 'Build papers, publish them and follow their statistics.',
          tips: ['Publishing may need a second person to approve (see Approvals).',
            'A test can only be edited while it is a draft.'],
        },
      },
      {
        to: '/admin/series', label: 'Test series', icon: Library, perms: ['series.view'], shortcut: 's', keywords: 'packages price',
        help: { summary: 'The packages students buy or enrol in.', tips: ['Archived series stay available to students already enrolled.'] },
      },
    ],
  },
  {
    label: 'People',
    items: [
      {
        to: '/admin/users', label: 'Users', icon: Users, perms: ['user.view'], shortcut: 'u', keywords: 'staff students roles',
        help: {
          summary: 'Accounts, roles and subject scopes.',
          tips: ['Changing roles signs the user out everywhere.', 'You can only grant permissions you hold yourself.'],
        },
      },
    ],
  },
  {
    label: 'Governance',
    items: [
      {
        to: '/admin/approvals', label: 'Approvals', icon: Stamp, perms: [], shortcut: 'a', keywords: 'maker checker pending review',
        help: {
          summary: 'Sensitive actions (publishing a test, final ranks, ...) wait here for a second person.',
          tips: ['You cannot approve your own request.', 'Approving runs the action immediately, as you.',
            'Requests expire after the time set in the policy.'],
        },
      },
      {
        to: '/admin/audit', label: 'Audit log', icon: ScrollText, perms: ['audit.view'], shortcut: 'l', keywords: 'history who changed',
        help: {
          summary: 'Every admin change: who, what, when, from where, and the before/after difference.',
          tips: ['Open a row to see the exact field-level changes.', 'The log is append-only: nobody can edit or delete it.',
            'Exports run in the background (see Jobs).'],
        },
      },
      {
        to: '/admin/roles', label: 'Roles & permissions', icon: KeyRound, perms: ['role.view'], shortcut: 'r', keywords: 'rbac access',
        help: {
          summary: 'Which role may do what. Changes apply on the next request, with no need to sign in again.',
          tips: ['Super admin always has every permission; students have none.',
            'Sensitive permissions are marked. Grant them carefully.'],
        },
      },
    ],
  },
  {
    label: 'Platform',
    items: [
      {
        to: '/admin/jobs', label: 'Background jobs', icon: Cpu, perms: [], shortcut: 'j', keywords: 'exports queue dead letter',
        help: {
          summary: 'Long tasks (exports, re-evaluations, imports) with progress, retry and cancel.',
          tips: ['Failed jobs retry automatically with backoff; after the last attempt they show as Dead.'],
        },
      },
      {
        to: '/admin/security', label: 'Security', icon: ShieldCheck, perms: [], shortcut: 'x', keywords: '2fa mfa sessions ip',
        help: {
          summary: 'Your 2FA and devices; for security admins, the IP allow-list and approval policies.',
          tips: ['Keep your recovery codes somewhere safe. Each works once.',
            '"Log out all devices" ends every session, including this one unless you keep it.'],
        },
      },
    ],
  },
]

export const ALL_NAV_ITEMS = ADMIN_NAV.flatMap((g) => g.items)

/** Help for a path: the most specific nav item that prefixes it. */
export function navItemFor(pathname: string): NavItem | undefined {
  return [...ALL_NAV_ITEMS].sort((a, b) => b.to.length - a.to.length)
    .find((i) => (i.to === '/admin' ? pathname === '/admin' : pathname.startsWith(i.to)))
}

