import { defineStore } from 'pinia'
import * as api from '@/api'
import type { GuestProfileDraft, ProfilePhotoView } from '@/types'

const IDEMPOTENCY_KEY = 'profile-submission-key'

export const useProfileStore = defineStore('guest-profile', {
  state: () => ({
    draft: null as GuestProfileDraft | null,
    photos: [] as ProfilePhotoView[],
    loading: false,
  }),
  actions: {
    async load(): Promise<void> {
      this.loading = true
      try {
        const [draft, photos] = await Promise.all([api.getDraft(), api.listPhotos()])
        this.draft = draft
        this.photos = photos
      } finally {
        this.loading = false
      }
    },
    async save(values: Record<string, unknown>): Promise<void> {
      this.draft = await api.saveDraft(values)
    },
    async removePhoto(photoId: number): Promise<void> {
      await api.deletePhoto(photoId)
      this.photos = this.photos.filter((photo) => photo.id !== photoId)
    },
    addPhoto(photo: ProfilePhotoView): void {
      if (photo.category === 'AVATAR') {
        this.photos = this.photos.filter((item) => item.category !== 'AVATAR')
      }
      this.photos.push(photo)
    },
    async submit(): Promise<{ id: number; status: string; reviewDeadlineAt: string }> {
      const key = idempotencyKey()
      return api.submitProfile(key)
    },
  },
})

function idempotencyKey(): string {
  const existing = sessionStorage.getItem(IDEMPOTENCY_KEY)
  if (existing) return existing
  const key = crypto.randomUUID()
  sessionStorage.setItem(IDEMPOTENCY_KEY, key)
  return key
}
