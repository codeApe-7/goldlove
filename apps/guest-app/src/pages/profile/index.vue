<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fieldDefinitions, uploadPhoto } from '@/api'
import { useProfileStore } from '@/stores/profile'
import { validateProfileForm } from '@/validators/profile'
import { choosePhotos } from '@/adapters/media'
import type { GuestFieldDefinition, PhotoUploadResult } from '@/types'
import SectionCard from '@/components/SectionCard.vue'
import ArchiveTabBar from '@/components/ArchiveTabBar.vue'
import AppButton from '@/components/AppButton.vue'
import AppFormRow from '@/components/AppFormRow.vue'
import AppInput from '@/components/AppInput.vue'
import AppSelect from '@/components/AppSelect.vue'
import AppWheelSelect from '@/components/AppWheelSelect.vue'
import AppDateField from '@/components/AppDateField.vue'
import AppCascadeField from '@/components/AppCascadeField.vue'
import AppRadioTiles from '@/components/AppRadioTiles.vue'
import AppCheckbox from '@/components/AppCheckbox.vue'
import AppUploader from '@/components/AppUploader.vue'
import AppProgressMeter from '@/components/AppProgressMeter.vue'
import type { UploaderItem } from '@/components/AppUploader.vue'
import type { CompletionItem } from '@/components/AppProgressMeter.vue'
import {
  profileCompletion,
  profileGroup,
  remainingLifePhotoSlots,
} from '@/utils/presentation'
import { profileControl } from '@/utils/profileControls'
import {
  draftToProfileValues,
  profileValuesToDraftPayload,
} from '@/utils/profileFields'

const MAX_LIFE_PHOTOS = 6
const GENDER_ICONS: Record<string, string> = { 男: 'user', 女: 'user', 不公开: 'lock' }

const store = useProfileStore()
const definitions = ref<GuestFieldDefinition[]>([])
const values = reactive<Record<string, string | number | boolean | null>>({})
const saving = ref(false)
// 表单是否已用服务端草稿填充过。onShow 会重复触发，只认第一次。
const hydrated = ref(false)
// 上传中的进度按分类记录，用于渲染进度环；上传结束即删除。
const uploadProgress = reactive<Record<string, number>>({})

const missing = computed(() => validateProfileForm(definitions.value, values))
const avatar = computed(() => store.avatar)
const lifePhotos = computed(() => store.lifePhotos)
const completion = computed(() =>
  profileCompletion(definitions.value, values, Boolean(avatar.value)),
)

const fieldGroups = computed(() => [
  { key: 'basic', title: '基本资料', items: itemsOf('basic') },
  { key: 'career', title: '职业与收入', items: itemsOf('career') },
  { key: 'social', title: '社交账号', items: itemsOf('social') },
  { key: 'more', title: '更多资料', items: itemsOf('more') },
].filter((group) => group.items.length > 0))

const completionItems = computed<CompletionItem[]>(() => [
  ...fieldGroups.value.map((group) => ({
    label: group.title,
    tone: groupTone(group.items),
  })),
  {
    label: '照片资料',
    tone: photoTone(),
  },
])

const avatarItems = computed<UploaderItem[]>(() => {
  if (uploadProgress.AVATAR !== undefined) {
    return [{ key: 'avatar-uploading', url: avatar.value?.previewUrl ?? '', progress: uploadProgress.AVATAR }]
  }
  return avatar.value
    ? [{ key: avatar.value.objectKey, url: avatar.value.previewUrl }]
    : []
})

const lifeItems = computed<UploaderItem[]>(() => {
  const uploaded = lifePhotos.value.map((photo) => ({
    key: photo.objectKey,
    url: photo.previewUrl,
  }))
  if (uploadProgress.LIFE !== undefined) {
    uploaded.push({ key: 'life-uploading', url: '', progress: uploadProgress.LIFE } as UploaderItem)
  }
  return uploaded
})

onShow(async () => {
  await Promise.all([loadDefinitions(), store.load()])
  // 只在首次进入时用服务端草稿填充表单。onShow 会被重复触发——H5 选图打开系统文件框
  // 再回来算一次页面显示，切到「我的」再切回来也算一次——那时用服务端数据覆盖
  // 会把用户还没保存的输入整片清空（draftToProfileValues 对缺失字段返回 ''）。
  if (!hydrated.value) {
    if (store.draft) {
      Object.assign(values, draftToProfileValues(store.draft))
    }
    hydrated.value = true
  }
})

async function loadDefinitions(): Promise<void> {
  try {
    definitions.value = await fieldDefinitions()
  } catch (error) {
    toast(error instanceof Error ? error.message : '字段加载失败')
  }
}

function itemsOf(group: string): GuestFieldDefinition[] {
  return definitions.value.filter((item) => profileGroup(item.fieldCode) === group)
}

function isFilled(definition: GuestFieldDefinition): boolean {
  const value = values[definition.fieldCode]
  return value !== null && value !== undefined && value !== '' && value !== false
}

function groupTone(items: GuestFieldDefinition[]): CompletionItem['tone'] {
  const filled = items.filter(isFilled).length
  if (filled === items.length) return 'done'
  return filled === 0 ? 'todo' : 'partial'
}

function photoTone(): CompletionItem['tone'] {
  if (!avatar.value) return 'todo'
  return lifePhotos.value.length > 0 ? 'done' : 'partial'
}

function textValue(code: string): string {
  const value = values[code]
  return value === null || value === undefined ? '' : String(value)
}

function setValue(code: string, value: string | boolean): void {
  values[code] = value
}

function toast(message: string, icon: 'none' | 'success' = 'none'): void {
  uni.showToast({ title: message, icon })
}

async function choosePhoto(category: 'AVATAR' | 'LIFE'): Promise<void> {
  const count = category === 'AVATAR' ? 1 : remainingLifePhotoSlots(store.photos)
  if (count <= 0) {
    toast('已达数量上限')
    return
  }
  try {
    const photos = await choosePhotos(count)
    for (const photo of photos) {
      uploadProgress[category] = 0
      try {
        const uploaded = await uploadPhoto(photo, category, (percent) => {
          uploadProgress[category] = percent
        })
        store.addUploaded(uploaded.data as PhotoUploadResult)
      } finally {
        delete uploadProgress[category]
      }
    }
    toast('已上传，保存后生效', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    toast(message.includes('cancel') ? '未选择照片' : message || '操作失败')
  }
}

function removePhoto(objectKey: string): void {
  store.removeByObjectKey(objectKey)
  toast('已移除，保存后生效', 'success')
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const draft = await store.save(
      profileValuesToDraftPayload(values, store.draft?.version ?? null),
    )
    // 没有审核环节，保存即对管理员可见。
    toast(draft?.status === 'COMPLETED' ? '已保存，资料完整' : '已保存', 'success')
  } catch (error) {
    toast(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="archive-page profile-page">
    <SectionCard>
      <view class="meter-wrap">
        <AppProgressMeter
          :percent="completion"
          title="档案完整度"
          :hint="missing.length > 0 ? `还需完善：${missing.join('、')}` : '资料已完整，感谢配合'"
          :items="completionItems"
        />
      </view>
    </SectionCard>

    <SectionCard
      v-for="group in fieldGroups"
      :key="group.key"
      :title="group.title"
      :meta="`${group.items.filter(isFilled).length}/${group.items.length}`"
      :clip="false"
    >
      <view class="form-body">
        <AppFormRow
          v-for="definition in group.items"
          :key="definition.fieldCode"
          :label="definition.label"
          :required="definition.required"
          :optional="!definition.required"
        >
          <AppRadioTiles
            v-if="profileControl(definition) === 'radio-tiles'"
            :model-value="textValue(definition.fieldCode)"
            :options="definition.options"
            :icons="GENDER_ICONS"
            :hint="definition.instructions"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppCascadeField
            v-else-if="profileControl(definition) === 'cascade'"
            :model-value="textValue(definition.fieldCode)"
            :placeholder="definition.instructions || '请选择所在城市 / 地区'"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppDateField
            v-else-if="profileControl(definition) === 'date'"
            :model-value="textValue(definition.fieldCode)"
            :placeholder="definition.instructions || '请选择日期'"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppWheelSelect
            v-else-if="profileControl(definition) === 'wheel-select'"
            :model-value="textValue(definition.fieldCode)"
            :options="definition.options"
            :title="`请选择${definition.label}`"
            :placeholder="definition.instructions || '请选择'"
            mask-private
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppSelect
            v-else-if="profileControl(definition) === 'searchable-select'"
            :model-value="textValue(definition.fieldCode)"
            :options="definition.options"
            :placeholder="definition.instructions || '请选择'"
            searchable
            :search-placeholder="`搜索${definition.label}关键词`"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppSelect
            v-else-if="profileControl(definition) === 'select'"
            :model-value="textValue(definition.fieldCode)"
            :options="definition.options"
            :placeholder="definition.instructions || '请选择'"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppCheckbox
            v-else-if="profileControl(definition) === 'checkbox'"
            :model-value="Boolean(values[definition.fieldCode])"
            @update:model-value="setValue(definition.fieldCode, $event)"
          >{{ definition.instructions || definition.label }}</AppCheckbox>
          <AppInput
            v-else-if="profileControl(definition) === 'textarea'"
            type="textarea"
            :model-value="textValue(definition.fieldCode)"
            :placeholder="definition.instructions || '请输入'"
            :maxlength="200"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppInput
            v-else-if="profileControl(definition) === 'number'"
            type="digit"
            :model-value="textValue(definition.fieldCode)"
            :placeholder="definition.instructions || '请输入数字'"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
          <AppInput
            v-else
            :model-value="textValue(definition.fieldCode)"
            :placeholder="definition.instructions || '请输入'"
            @update:model-value="setValue(definition.fieldCode, $event)"
          />
        </AppFormRow>
      </view>
    </SectionCard>

    <SectionCard title="个人影像" :meta="`${lifePhotos.length + (avatar ? 1 : 0)}/7`">
      <view class="photo-body">
        <view class="photo-row">
          <view class="photo-copy">
            <text class="photo-label">头像<text class="required"> *</text></text>
            <text class="photo-help">用于档案身份展示</text>
          </view>
          <AppUploader
            mode="avatar"
            :items="avatarItems"
            empty-label="上传头像"
            footer=""
            @add="choosePhoto('AVATAR')"
            @remove="removePhoto"
          />
        </view>

        <view class="photo-block">
          <view class="photo-copy">
            <text class="photo-label">生活照</text>
            <text class="photo-help">最多 {{ MAX_LIFE_PHOTOS }} 张，建议包含正面照和生活场景</text>
          </view>
          <AppUploader
            :items="lifeItems"
            :max="MAX_LIFE_PHOTOS"
            @add="choosePhoto('LIFE')"
            @remove="removePhoto"
          />
        </view>
      </view>
    </SectionCard>

    <view class="actions">
      <AppButton block :disabled="saving" @tap="save">
        {{ saving ? '保存中' : '保存档案' }}
      </AppButton>
    </view>
    <ArchiveTabBar current="profile" />
  </view>
</template>

<style lang="scss" scoped>
@use '@/styles/tokens.scss' as *;

.profile-page {
  padding-top: $ds-space-3;
}

.meter-wrap {
  padding: $ds-space-4;
}

.form-body {
  padding: 0 $ds-space-3;
}

.photo-body {
  padding: $ds-space-4;
}

.photo-row {
  padding-bottom: $ds-space-4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $ds-space-3;
  border-bottom: $ds-hairline solid #edebe7;
}

.photo-block {
  padding-top: $ds-space-4;
}

.photo-copy {
  min-width: 0;
}

.photo-label,
.photo-help {
  display: block;
}

.photo-label {
  @include ds-body-2;
  color: $ds-graphite;
  font-weight: 500;
}

.required {
  color: $ds-error;
}

.photo-help {
  margin-top: $ds-space-1;
  @include ds-caption;
  color: $ds-gray;
}

.photo-block .photo-copy {
  margin-bottom: $ds-space-3;
}

.actions {
  position: sticky;
  bottom: calc(104rpx + env(safe-area-inset-bottom));
  z-index: 4;
  margin: $ds-space-4 -10rpx -6rpx;
  padding: $ds-space-2 10rpx;
  background: rgba(245, 244, 242, 0.94);
  backdrop-filter: blur(12px);
}
</style>
