import { request, uploadPhoto } from './request'
import type {
  AuthorizationDocumentView,
  ConsentView,
  GuestFieldDefinition,
  GuestProfileDraft,
  GuestSession,
  PhotoUploadResult,
  ProfilePhotoView,
} from '@/types'

export function activate(phone: string, initialCredential: string, newPassword: string) {
  return request<GuestSession>({
    url: '/guest/auth/activate',
    method: 'POST',
    data: { phone, initialCredential, newPassword },
  })
}

export function login(phone: string, password: string) {
  return request<GuestSession>({
    url: '/guest/auth/login',
    method: 'POST',
    data: { phone, password },
  })
}

export function logout() {
  return request<null>({ url: '/guest/auth/logout', method: 'POST' })
}

export function currentAuthorizationDocument() {
  return request<AuthorizationDocumentView>({ url: '/public/authorization-documents/current' })
}

export function currentConsent() {
  return request<ConsentView | null>({ url: '/guest/consents/current' })
}

export function acceptConsent(authorizationDocumentVersion: string, sourcePage: string) {
  return request<ConsentView>({
    url: '/guest/consents',
    method: 'POST',
    data: { authorizationDocumentVersion, accepted: true, sourcePage },
  })
}

export function fieldDefinitions() {
  return request<GuestFieldDefinition[]>({ url: '/guest/profile/field-definitions' })
}

export function getDraft() {
  return request<GuestProfileDraft>({ url: '/guest/profile/draft' })
}

export function saveDraft(payload: Record<string, unknown>) {
  return request<GuestProfileDraft>({
    url: '/guest/profile/draft',
    method: 'PUT',
    data: payload,
  })
}

export function profileStatus() {
  return request<{
    status: string
    pendingRevisionId: number | null
    currentApprovedRevisionId: number | null
  }>({ url: '/guest/profile/status' })
}

export function listPhotos() {
  return request<ProfilePhotoView[]>({ url: '/guest/profile/photos' })
}

export function submitProfile(idempotencyKey: string) {
  return request<{ id: number; status: string; reviewDeadlineAt: string }>({
    url: '/guest/profile/submissions',
    method: 'POST',
    header: { 'Idempotency-Key': idempotencyKey },
  })
}

export { uploadPhoto }
