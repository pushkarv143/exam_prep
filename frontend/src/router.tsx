import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppLayout, AuthLayout, PublicLayout } from '@/components/layout/Layouts'
import { GuestOnly, RequireAuth } from '@/components/routing/Guards'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { AdminLayout } from '@/features/admin/AdminLayout'
import { RequirePerm } from '@/features/admin/shell/RequirePerm'
import { PageLoader } from '@/components/common/States'

// Route-level code splitting: the landing page never downloads dashboard, exam or admin code.
const LandingPage = lazy(() => import('@/features/public/LandingPage'))
const SeriesListPage = lazy(() => import('@/features/series/SeriesListPage'))
const SeriesDetailPage = lazy(() => import('@/features/series/SeriesDetailPage'))
const LoginPage = lazy(() => import('@/features/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'))
const ForgotPasswordPage = lazy(() => import('@/features/auth/PasswordPages').then((m) => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('@/features/auth/PasswordPages').then((m) => ({ default: m.ResetPasswordPage })))
const DashboardPage = lazy(() => import('@/features/student/DashboardPage'))
const MySeriesPage = lazy(() => import('@/features/student/MySeriesPage'))
const ProfilePage = lazy(() => import('@/features/student/ProfilePage'))

const TestInstructionsPage = lazy(() => import('@/features/exam/TestInstructionsPage'))
const ExamPage = lazy(() => import('@/features/exam/ExamPage'))
const ResultPage = lazy(() => import('@/features/results/ResultPage'))
const SolutionsPage = lazy(() => import('@/features/results/SolutionsPage'))
const LeaderboardPage = lazy(() => import('@/features/results/LeaderboardPage'))

const AdminDashboardPage = lazy(() => import('@/features/admin/AdminDashboardPage'))
const QuestionsPage = lazy(() => import('@/features/admin/QuestionsPage'))
const QuestionEditorPage = lazy(() => import('@/features/admin/QuestionEditorPage'))
const ReviewQueuePage = lazy(() => import('@/features/admin/ReviewQueuePage'))
const TestsPage = lazy(() => import('@/features/admin/TestsPage'))
const TestBuilderPage = lazy(() => import('@/features/admin/TestBuilderPage'))
const TestStatsPage = lazy(() => import('@/features/admin/TestStatsPage'))
const SeriesAdminPage = lazy(() => import('@/features/admin/SeriesAdminPage'))
const UsersAdminPage = lazy(() => import('@/features/admin/UsersAdminPage'))
const AdminHomePage = lazy(() => import('@/features/admin/AdminHomePage'))
const ApprovalsPage = lazy(() => import('@/features/admin/ApprovalsPage'))
const ApprovalDetailPage = lazy(() => import('@/features/admin/ApprovalsPage').then((m) => ({ default: m.ApprovalDetailPage })))
const AuditLogPage = lazy(() => import('@/features/admin/AuditLogPage'))
const RolesPage = lazy(() => import('@/features/admin/RolesPage'))
const JobsPage = lazy(() => import('@/features/admin/JobsPage'))
const SecurityPage = lazy(() => import('@/features/admin/SecurityPage'))

export const router = createBrowserRouter([
  {
    element: <PublicLayout />,
    children: [
      { index: true, element: <LandingPage /> },
      { path: 'series', element: <SeriesListPage /> },
      { path: 'series/:slug', element: <SeriesDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      {
        element: <GuestOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
      // Not guest-only: a logged-in user may open a reset link from their email too.
      { path: 'reset-password', element: <ResetPasswordPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'my/series', element: <MySeriesPage /> },
          { path: 'profile', element: <ProfilePage /> },
          { path: 'tests/:testId', element: <TestInstructionsPage /> },
          { path: 'tests/:testId/leaderboard', element: <LeaderboardPage /> },
          { path: 'attempts/:attemptId/result', element: <ResultPage /> },
          { path: 'attempts/:attemptId/solutions', element: <SolutionsPage /> },
        ],
      },
      // The exam runs full-screen without the site chrome.
      { path: 'exam/:attemptId', element: <Suspense fallback={<PageLoader />}><ExamPage /></Suspense> },
    ],
  },
  {
    path: 'admin',
    element: <RequireAuth staff><AdminLayout /></RequireAuth>,
    handle: { crumb: 'Admin' },
    children: [
      { index: true, element: <AdminHomePage /> },
      { path: 'dashboard', handle: { crumb: 'Dashboard' }, element: <RequirePerm any={['dashboard.view']}><AdminDashboardPage /></RequirePerm> },
      {
        path: 'questions', handle: { crumb: 'Question bank' },
        children: [
          { index: true, element: <RequirePerm any={['question.view']}><QuestionsPage /></RequirePerm> },
          { path: 'new', handle: { crumb: 'New question' }, element: <RequirePerm any={['question.create']}><QuestionEditorPage /></RequirePerm> },
          { path: ':id', handle: { crumb: 'Question studio' }, element: <RequirePerm any={['question.view']}><QuestionEditorPage /></RequirePerm> },
        ],
      },
      {
        path: 'review', handle: { crumb: 'Review queue' },
        element: <RequirePerm any={['question.review', 'question.approve', 'question.publish', 'question.create']}><ReviewQueuePage /></RequirePerm>,
      },
      {
        path: 'tests', handle: { crumb: 'Tests' },
        children: [
          { index: true, element: <RequirePerm any={['test.view']}><TestsPage /></RequirePerm> },
          { path: ':testId', handle: { crumb: 'Builder' }, element: <RequirePerm any={['test.view']}><TestBuilderPage /></RequirePerm> },
          { path: ':testId/stats', handle: { crumb: 'Statistics' }, element: <RequirePerm any={['result.view']}><TestStatsPage /></RequirePerm> },
        ],
      },
      { path: 'series', handle: { crumb: 'Test series' }, element: <RequirePerm any={['series.view']}><SeriesAdminPage /></RequirePerm> },
      { path: 'users', handle: { crumb: 'Users' }, element: <RequirePerm any={['user.view']}><UsersAdminPage /></RequirePerm> },
      {
        path: 'approvals', handle: { crumb: 'Approvals' },
        children: [
          { index: true, element: <ApprovalsPage /> },
          { path: ':id', handle: { crumb: 'Request' }, element: <ApprovalDetailPage /> },
        ],
      },
      { path: 'audit', handle: { crumb: 'Audit log' }, element: <RequirePerm any={['audit.view']}><AuditLogPage /></RequirePerm> },
      { path: 'roles', handle: { crumb: 'Roles & permissions' }, element: <RequirePerm any={['role.view']}><RolesPage /></RequirePerm> },
      { path: 'jobs', handle: { crumb: 'Background jobs' }, element: <JobsPage /> },
      { path: 'security', handle: { crumb: 'Security' }, element: <SecurityPage /> },
    ],
  },
])
