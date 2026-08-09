import { http, unwrap } from './http'
import type {
  AdminDashboardStats,
  AdminSession,
  PageView,
  ProfileFieldDefinitionView,
  ProfileReviewDetail,
  ProfileReviewListItem,
  ProvisionedGuest,
} from '@/types'

export interface AuthorizationDocumentView {
  version: string
  title: string
  content: string
  contentSha256: string
  effectiveAt: string
}

export function login(username: string, password: string): Promise<AdminSession> {
  return unwrap(http.post('/admin/auth/login', { username, password }))
}

export function logout(): Promise<null> {
  return unwrap(http.post('/admin/auth/logout'))
}

export function dashboardStats(): Promise<AdminDashboardStats> {
  return unwrap(http.get('/admin/dashboard/stats'))
}

export function provisionGuest(payload: {
  phone: string
  paymentReference: string
  amountMinor: number
  paidAt: string
  authorizationDocumentVersion: string
  note?: string | null
}): Promise<ProvisionedGuest> {
  return unwrap(http.post('/admin/accounts', payload))
}

export function reissueCredential(phone: string): Promise<ProvisionedGuest> {
  return unwrap(http.post('/admin/accounts/activation-credentials/reissue', { phone }))
}

export function currentAuthorizationDocumentVersion(): Promise<AuthorizationDocumentView> {
  return unwrap(http.get('/public/authorization-documents/current'))
}

export function listFieldDefinitions(
  page: number,
  size: number,
): Promise<PageView<ProfileFieldDefinitionView>> {
  return unwrap(http.get('/admin/profile-field-definitions', { params: { page, size } }))
}

export function createFieldDefinition(payload: Record<string, unknown>) {
  return unwrap(http.post('/admin/profile-field-definitions', payload))
}

export function updateFieldDefinition(id: number, payload: Record<string, unknown>) {
  return unwrap(http.patch(`/admin/profile-field-definitions/${id}`, payload))
}

export function listReviews(
  params: Record<string, string | number | undefined>,
): Promise<PageView<ProfileReviewListItem>> {
  return unwrap(http.get('/admin/profile-reviews', { params }))
}

export function reviewDetail(revisionId: number): Promise<ProfileReviewDetail> {
  return unwrap(http.get(`/admin/profile-reviews/${revisionId}`))
}

export function approveReview(revisionId: number, expectedVersion: number) {
  return unwrap(http.post(`/admin/profile-reviews/${revisionId}/approve`, { expectedVersion }))
}

export function rejectReview(
  revisionId: number,
  expectedVersion: number,
  reasonCode: string | null,
  comment: string,
) {
  return unwrap(
    http.post(`/admin/profile-reviews/${revisionId}/reject`, {
      expectedVersion,
      reasonCode,
      comment,
    }),
  )
}
