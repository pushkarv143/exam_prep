import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppLayout, AuthLayout, PublicLayout } from '@/components/layout/Layouts'
import { GuestOnly, RequireAuth } from '@/components/routing/Guards'
import { NotFoundPage } from '@/features/errors/ErrorPages'
import { AdminIndex, AdminLayout } from '@/features/admin/AdminLayout'
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
const TestsPage = lazy(() => import('@/features/admin/TestsPage'))
const TestBuilderPage = lazy(() => import('@/features/admin/TestBuilderPage'))
const TestStatsPage = lazy(() => import('@/features/admin/TestStatsPage'))
const SeriesAdminPage = lazy(() => import('@/features/admin/SeriesAdminPage'))
const UsersAdminPage = lazy(() => import('@/features/admin/UsersAdminPage'))

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
    element: <RequireAuth roles={['ADMIN', 'TEACHER']}><AdminLayout /></RequireAuth>,
    children: [
      { index: true, element: <AdminIndex /> },
      { path: 'dashboard', element: <RequireAuth roles={['ADMIN']}><AdminDashboardPage /></RequireAuth> },
      { path: 'questions', element: <QuestionsPage /> },
      { path: 'questions/new', element: <QuestionEditorPage /> },
      { path: 'questions/:id', element: <QuestionEditorPage /> },
      { path: 'tests', element: <TestsPage /> },
      { path: 'tests/:testId', element: <TestBuilderPage /> },
      { path: 'tests/:testId/stats', element: <TestStatsPage /> },
      { path: 'series', element: <RequireAuth roles={['ADMIN']}><SeriesAdminPage /></RequireAuth> },
      { path: 'users', element: <RequireAuth roles={['ADMIN']}><UsersAdminPage /></RequireAuth> },
    ],
  },
])
