import { defineStore } from 'pinia'
import * as api from '@/api'
import type { GuestProfileDraft, PhotoUploadResult } from '@/types'


export type LocalPhoto = Pick<PhotoUploadResult, 'objectKey' | 'category' | 'previewUrl'>

export const useProfileStore = defineStore('guest-profile', {
  state: () => ({
    draft: null as GuestProfileDraft | null,
    photos: [] as LocalPhoto[],
    loading: false,
  }),
  getters: {
    avatar: (state): LocalPhoto | undefined =>
      state.photos.find((photo) => photo.category === 'AVATAR'),
    lifePhotos: (state): LocalPhoto[] =>
      state.photos.filter((photo) => photo.category === 'LIFE'),
  },
  actions: {
    async load(): Promise<void> {
      this.loading = true
      try {
        const [draft, photos] = await Promise.all([api.getDraft(), api.listPhotos()])
        this.draft = draft
        this.photos = photos.map((photo) => ({
          objectKey: photo.objectKey,
          category: photo.category,
          previewUrl: photo.previewUrl,
        }))
      } finally {
        this.loading = false
      }
    },
    addUploaded(upload: PhotoUploadResult): void {
      if (upload.category === 'AVATAR') {
        this.photos = this.photos.filter((photo) => photo.category !== 'AVATAR')
      }
      this.photos.push({
        objectKey: upload.objectKey,
        category: upload.category,
        previewUrl: upload.previewUrl,
      })
    },
    removeByObjectKey(objectKey: string): void {
      this.photos = this.photos.filter((photo) => photo.objectKey !== objectKey)
    },
    async save(values: Record<string, unknown>): Promise<GuestProfileDraft> {
      const avatar = this.avatar
      const draft = await api.saveDraft({
        ...values,
        photos: {
          avatar: avatar?.objectKey ?? null,
          life: this.lifePhotos.map((photo) => photo.objectKey),
        },
      })
      this.draft = draft
      await this.refreshPhotos()
      return draft
    },
    async refreshPhotos(): Promise<void> {
      const photos = await api.listPhotos()
      this.photos = photos.map((photo) => ({
        objectKey: photo.objectKey,
        category: photo.category,
        previewUrl: photo.previewUrl,
      }))
    },
  },
})

