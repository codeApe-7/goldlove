<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fieldDefinitions, uploadPhoto } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { validateProfileForm } from '@/validators/profile'
import { choosePhotos } from '@/adapters/media'
import type { GuestFieldDefinition, ProfilePhotoView } from '@/types'

const store = useProfileStore()
const definitions = ref<GuestFieldDefinition[]>([])
const values = reactive<Record<string, string | number | boolean | null>>({})
const saving = ref(false)
const submitting = ref(false)

const missing = computed(() => validateProfileForm(definitions.value, values))

onShow(async () => {
  await Promise.all([loadDefinitions(), store.load()])
  if (store.draft) {
    Object.assign(values, {
      gender: store.draft.gender ?? '',
      birthDate: store.draft.birthDate ?? '',
      heightCm: store.draft.heightCm ?? '',
      education: store.draft.education ?? '',
      occupation: store.draft.occupation ?? '',
      incomeRange: store.draft.incomeRange ?? '',
      city: store.draft.city ?? '',
      wechatId: store.draft.wechatId ?? '',
      douyinId: store.draft.douyinId ?? '',
      douyinNickname: store.draft.douyinNickname ?? '',
      douyinProfileUrl: store.draft.douyinProfileUrl ?? '',
    })
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
  const count = category === 'AVATAR' ? 1 : Math.max(0, 6 - store.photos.length)
  if (count <= 0) {
    uni.showToast({ title: '已达数量上限', icon: 'none' })
    return
  }
  try {
    const photos = await choosePhotos(count)
    for (const photo of photos) {
      const uploaded = await uploadPhoto(photo, category)
      store.addPhoto(uploaded.data as ProfilePhotoView)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    uni.showToast({
      title: message.includes('cancel') ? '未选择照片' : message || '操作失败',
      icon: 'none',
    })
  }
}

async function removePhoto(photo: ProfilePhotoView): Promise<void> {
  try {
    await store.removePhoto(photo.id)
    uni.showToast({ title: '已删除', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败', icon: 'none' })
  }
}

async function save(): Promise<void> {
  saving.value = true
  try {
    await store.save({ ...values })
    uni.showToast({ title: '草稿已保存', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function submit(): Promise<void> {
  if (missing.value.length > 0) {
    uni.showToast({ title: `缺少必填：${missing.value.join('、')}`, icon: 'none' })
    return
  }
  if (!store.photos.some((photo) => photo.category === 'AVATAR')) {
    uni.showToast({ title: '请上传头像', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await save()
    await store.submit()
    uni.switchTab({ url: '/pages/status/index' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="section-title">基本信息</view>
    <view class="card">
      <view v-for="definition in definitions" :key="definition.fieldCode" class="form-row">
        <text class="label">{{ definition.label }}{{ definition.required ? ' *' : '' }}</text>
        <picker
          v-if="definition.dataType === 'SINGLE_OPTION'"
          :range="definition.options"
          @change="onOptionChange(definition, $event)"
        >
          <view class="picker-value">{{ values[definition.fieldCode] || '请选择' }}</view>
        </picker>
        <input
          v-else-if="definition.dataType === 'TEXT' || definition.dataType === 'LONG_TEXT'"
          v-model="values[definition.fieldCode]"
          class="input"
          type="text"
          :placeholder="definition.instructions || '请输入'"
        />
        <input
          v-else-if="definition.dataType === 'INTEGER' || definition.dataType === 'DECIMAL'"
          v-model="values[definition.fieldCode]"
          class="input"
          type="digit"
          :placeholder="definition.instructions || '请输入数字'"
        />
        <picker
          v-else-if="definition.dataType === 'DATE'"
          mode="date"
          @change="onDateChange(definition, $event)"
        >
          <view class="picker-value">{{ values[definition.fieldCode] || '请选择日期' }}</view>
        </picker>
        <switch
          v-else-if="definition.dataType === 'BOOLEAN'"
          :checked="Boolean(values[definition.fieldCode])"
          color="#B4556D"
          @change="onBooleanChange(definition, $event)"
        />
      </view>
    </view>

    <view class="section-title">照片</view>
    <view class="card">
      <view class="photo-section">
        <text class="label">头像（必填，1 张）</text>
        <view class="photo-list">
          <image
            v-for="photo in store.photos.filter((p) => p.category === 'AVATAR')"
            :key="photo.id"
            :src="photo.downloadUrl"
            class="photo"
            mode="aspectFill"
          />
          <view class="add" @tap="choosePhoto('AVATAR')">+</view>
        </view>
      </view>
      <view class="photo-section">
        <text class="label">生活照（最多 6 张）</text>
        <view class="photo-list">
          <view
            v-for="photo in store.photos.filter((p) => p.category === 'LIFE')"
            :key="photo.id"
            class="photo-wrap"
          >
            <image :src="photo.downloadUrl" class="photo" mode="aspectFill" />
            <text class="remove" @tap="removePhoto(photo)">×</text>
          </view>
          <view class="add" @tap="choosePhoto('LIFE')">+</view>
        </view>
      </view>
    </view>

    <view class="actions">
      <button class="save" :disabled="saving" @tap="save">保存草稿</button>
      <button class="submit" :disabled="submitting" @tap="submit">提交审核</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding: 30rpx;
}
.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #46323a;
  margin: 20rpx 0 16rpx;
}
.card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 30rpx;
  margin-bottom: 24rpx;
}
.form-row {
  margin-bottom: 28rpx;
}
.label {
  display: block;
  font-size: 28rpx;
  color: #3b3034;
  margin-bottom: 12rpx;
}
.input,
.picker-value {
  height: 80rpx;
  line-height: 80rpx;
  border-bottom: 2rpx solid #f0e6ea;
  font-size: 30rpx;
}
.photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.photo {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
}
.photo-wrap {
  position: relative;
}
.remove {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  background: #b4556d;
  color: #ffffff;
}
.add {
  width: 160rpx;
  height: 160rpx;
  border: 2rpx dashed #d9c2ca;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  color: #b4556d;
}
.actions {
  display: flex;
  gap: 20rpx;
}
.save {
  flex: 1;
  background: #ffffff;
  color: #b4556d;
  border: 2rpx solid #b4556d;
  border-radius: 999rpx;
}
.submit {
  flex: 1;
  background: #b4556d;
  color: #ffffff;
  border-radius: 999rpx;
}
</style>
