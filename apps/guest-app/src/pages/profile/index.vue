<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fieldDefinitions, uploadPhoto } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { validateProfileForm } from '@/validators/profile'
import { choosePhotos } from '@/adapters/media'
import type { GuestFieldDefinition, PhotoUploadResult } from '@/types'
import SectionCard from '@/components/SectionCard.vue'
import AppIcon from '@/components/AppIcon.vue'
import ArchiveTabBar from '@/components/ArchiveTabBar.vue'
import {
  profileCompletion,
  profileGroup,
  remainingLifePhotoSlots,
} from '@/utils/presentation'
import {
  draftToProfileValues,
  profileValuesToDraftPayload,
} from '@/utils/profileFields'

const store = useProfileStore()
const definitions = ref<GuestFieldDefinition[]>([])
const values = reactive<Record<string, string | number | boolean | null>>({})
const saving = ref(false)

const missing = computed(() => validateProfileForm(definitions.value, values))
const avatar = computed(() => store.avatar)
const lifePhotos = computed(() => store.lifePhotos)
const completion = computed(() => profileCompletion(definitions.value, values, Boolean(avatar.value)))
const fieldGroups = computed(() => [
  { key: 'basic', title: '基本资料', items: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'basic') },
  { key: 'career', title: '职业与收入', items: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'career') },
  { key: 'social', title: '社交账号', items: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'social') },
  { key: 'more', title: '更多资料', items: definitions.value.filter((item) => profileGroup(item.fieldCode) === 'more') },
].filter((group) => group.items.length > 0))

onShow(async () => {
  await Promise.all([loadDefinitions(), store.load()])
  if (store.draft) {
    Object.assign(values, draftToProfileValues(store.draft))
  }
})

async function loadDefinitions(): Promise<void> {
  try {
    definitions.value = await fieldDefinitions()
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '字段加载失败', icon: 'none' })
  }
}

function onOptionChange(
  definition: GuestFieldDefinition,
  event: { detail: { value: number } },
): void {
  values[definition.fieldCode] = definition.options[event.detail.value] ?? ''
}

function onDateChange(
  definition: GuestFieldDefinition,
  event: { detail: { value: string } },
): void {
  values[definition.fieldCode] = event.detail.value
}

function onBooleanChange(
  definition: GuestFieldDefinition,
  event: Event,
): void {
  values[definition.fieldCode] = (
    event as Event & { detail: { value: boolean } }
  ).detail.value
}

async function choosePhoto(category: 'AVATAR' | 'LIFE'): Promise<void> {
  const count = category === 'AVATAR' ? 1 : remainingLifePhotoSlots(store.photos)
  if (count <= 0) {
    uni.showToast({ title: '已达数量上限', icon: 'none' })
    return
  }
  try {
    const photos = await choosePhotos(count)
    for (const photo of photos) {
      const uploaded = await uploadPhoto(photo, category)
      store.addUploaded(uploaded.data as PhotoUploadResult)
    }
    uni.showToast({ title: '已上传，保存后生效', icon: 'success' })
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    uni.showToast({
      title: message.includes('cancel') ? '未选择照片' : message || '操作失败',
      icon: 'none',
    })
  }
}

function removePhoto(photo: { objectKey: string }): void {
  store.removeByObjectKey(photo.objectKey)
  uni.showToast({ title: '已移除，保存后生效', icon: 'success' })
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const draft = await store.save(profileValuesToDraftPayload(values, store.draft?.version ?? null))
    // 没有审核环节，保存即对管理员可见。
    uni.showToast({
      title: draft?.status === 'COMPLETED' ? '已保存，资料完整' : '已保存',
      icon: 'success',
    })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="archive-page profile-page">
    <view class="profile-head">
      <view><text class="profile-kicker">档案完整度</text><text class="profile-hint">请确保填写信息真实、完整</text></view>
      <view class="completion"><text>完成度</text><strong class="archive-tabular">{{ completion }}%</strong></view>
    </view>
    <view class="progress-track"><view :style="{ width: `${completion}%` }" /></view>

    <SectionCard v-for="group in fieldGroups" :key="group.key" :title="group.title" :meta="`${group.items.filter((item) => values[item.fieldCode] !== '' && values[item.fieldCode] != null).length}/${group.items.length}`">
      <view v-for="definition in group.items" :key="definition.fieldCode" class="form-row">
        <view class="field-label">
          <text class="label">{{ definition.label }}<text v-if="definition.required" class="required"> *</text></text>
          <text v-if="group.key === 'social' && !definition.required" class="optional">选填</text>
        </view>
        <view class="field-side">
          <picker v-if="definition.dataType === 'SINGLE_OPTION'" :range="definition.options" @change="onOptionChange(definition, $event)">
            <view class="picker-value" :class="{ placeholder: !values[definition.fieldCode] }">{{ values[definition.fieldCode] || '请选择' }}</view>
          </picker>
          <input v-else-if="definition.dataType === 'TEXT' || definition.dataType === 'LONG_TEXT'" v-model="values[definition.fieldCode]" class="input" type="text" :placeholder="definition.instructions || '请输入'" />
          <input v-else-if="definition.dataType === 'INTEGER' || definition.dataType === 'DECIMAL'" v-model="values[definition.fieldCode]" class="input" type="digit" :placeholder="definition.instructions || '请输入数字'" />
          <picker v-else-if="definition.dataType === 'DATE'" mode="date" @change="onDateChange(definition, $event)">
            <view class="picker-value" :class="{ placeholder: !values[definition.fieldCode] }">{{ values[definition.fieldCode] || '请选择日期' }}</view>
          </picker>
          <switch v-else-if="definition.dataType === 'BOOLEAN'" :checked="Boolean(values[definition.fieldCode])" color="#0D0D0F" @change="onBooleanChange(definition, $event)" />
          <AppIcon v-if="definition.dataType !== 'BOOLEAN'" name="chevron" :size="18" />
        </view>
      </view>
    </SectionCard>

    <SectionCard title="个人影像" :meta="`${lifePhotos.length + (avatar ? 1 : 0)}/7`">
      <view class="photo-section avatar-section">
        <view><text class="photo-label">头像（必填）</text><text class="photo-help">用于档案身份展示</text></view>
        <view class="avatar-picker" @tap="choosePhoto('AVATAR')">
          <image v-if="avatar" :src="avatar.previewUrl" class="avatar-photo" mode="aspectFill" />
          <view v-else class="avatar-empty"><AppIcon name="user" :size="26" /></view>
          <view class="camera-dot"><AppIcon name="camera" :size="12" /></view>
        </view>
      </view>
      <view class="photo-section life-section">
        <view class="photo-copy"><text class="photo-label">生活照</text><text class="photo-help">最多 6 张，建议包含正面照和生活场景</text></view>
        <view class="photo-list">
          <view v-for="photo in lifePhotos" :key="photo.objectKey" class="photo-wrap">
            <image :src="photo.previewUrl" class="photo" mode="aspectFill" />
            <text class="remove" @tap="removePhoto(photo)">×</text>
          </view>
          <view v-if="lifePhotos.length < 6" class="add" @tap="choosePhoto('LIFE')"><text>＋</text><small>上传</small></view>
        </view>
      </view>
    </SectionCard>

    <view class="actions">
      <button class="archive-button-primary save-button" :disabled="saving" @tap="save">
        {{ saving ? '保存中' : '保存档案' }}
      </button>
    </view>
    <ArchiveTabBar current="profile" />
  </view>
</template>

<style lang="scss" scoped>
.profile-page {
  padding-top: 24rpx;
}
.profile-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.profile-kicker,
.profile-hint { display: block; }
.profile-kicker { font-size: 25rpx; font-weight: 600; }
.profile-hint { margin-top: 6rpx; color: #85868a; font-size: 20rpx; }
.completion {
  text-align: right;
}
.completion text,
.completion strong { display: block; }
.completion text { color: #85868a; font-size: 20rpx; }
.completion strong { margin-top: 5rpx; font-size: 24rpx; }
.progress-track {
  height: 6rpx;
  margin: 18rpx 0 32rpx;
  overflow: hidden;
  border-radius: 99rpx;
  background: #e4e2de;
}
.progress-track view {
  height: 100%;
  border-radius: inherit;
  background: #0d0d0f;
  transition: width 180ms ease;
}
.form-row {
  min-height: 82rpx;
  padding: 0 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  border-bottom: 1rpx solid #edebe7;
}
.form-row:last-child { border-bottom: 0; }
.field-label {
  flex: none;
  display: flex;
  align-items: center;
  gap: 9rpx;
}
.label {
  color: #29292d;
  font-size: 24rpx;
}
.required { color: #ef4444; }
.optional {
  color: #a0a1a4;
  font-size: 18rpx;
}
.field-side {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  color: #a0a1a4;
}
.field-side picker { max-width: 100%; }
.input,
.picker-value {
  min-width: 160rpx;
  max-width: 390rpx;
  height: 80rpx;
  color: #353539;
  text-align: right;
  font-size: 23rpx;
  line-height: 80rpx;
}
.placeholder { color: #a0a1a4; }
.field-side switch { transform: scale(0.72); transform-origin: right center; }
.photo-section {
  padding: 22rpx;
}
.avatar-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1rpx solid #edebe7;
}
.photo-label,
.photo-help { display: block; }
.photo-label { color: #29292d; font-size: 24rpx; }
.photo-help { margin-top: 7rpx; color: #929397; font-size: 20rpx; }
.avatar-picker {
  position: relative;
  width: 108rpx;
  height: 108rpx;
}
.avatar-photo,
.avatar-empty {
  width: 108rpx;
  height: 108rpx;
  border-radius: 12rpx;
}
.avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edebe7;
  color: #77787c;
}
.camera-dot {
  position: absolute;
  right: -7rpx;
  bottom: -7rpx;
  width: 36rpx;
  height: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3rpx solid #ffffff;
  border-radius: 50%;
  background: #0d0d0f;
  color: #ffffff;
}
.photo-copy { margin-bottom: 18rpx; }
.photo-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}
.photo-wrap { position: relative; aspect-ratio: 1; }
.photo {
  width: 100%;
  height: 100%;
  border-radius: 10rpx;
}
.remove {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: rgba(13, 13, 15, 0.72);
  color: #ffffff;
  text-align: center;
  font-size: 23rpx;
  line-height: 29rpx;
}
.add {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1rpx dashed #bfc0c3;
  border-radius: 10rpx;
  color: #85868a;
}
.add text { font-size: 34rpx; line-height: 1; }
.add small { margin-top: 7rpx; font-size: 19rpx; }
.actions {
  position: sticky;
  bottom: calc(104rpx + env(safe-area-inset-bottom));
  z-index: 4;
  margin: 28rpx -10rpx -6rpx;
  padding: 14rpx 10rpx;
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: 14rpx;
  background: rgba(247, 246, 243, 0.94);
  backdrop-filter: blur(12px);
}
</style>
