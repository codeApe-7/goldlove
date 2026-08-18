<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 18 })
</script>

<template>
  <view class="app-icon" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="name === 'authorization'">
        <rect x="4.25" y="2.75" width="15.5" height="18.5" rx="2.4" />
        <path d="M8 7.25h8M8.1 11.5l2.25 2.25 5-5" />
      </template>
      <template v-else-if="name === 'shield' || name === 'status'">
        <path d="M12 2.8c2.35 1.52 4.92 2.28 7.15 2.5v5.95c0 4.55-2.7 8.3-7.15 9.9-4.45-1.6-7.15-5.35-7.15-9.9V5.3C7.08 5.08 9.65 4.32 12 2.8Z" />
        <path d="M12 10.25v4.25" /><path d="M12 7.4h.01" />
      </template>
      <template v-else-if="name === 'document'">
        <path d="M6 2.75h7.7L18 7.05v14.2H6z" />
        <path d="M13.7 2.75v4.3H18M9 11h6M9 14.5h6M9 18h4" />
      </template>
      <template v-else-if="name === 'account'">
        <circle cx="12" cy="7" r="3.1" />
        <path d="M5.7 18.75v-1.2c0-3.2 2.5-5.35 6.3-5.35s6.3 2.15 6.3 5.35v1.2" />
        <rect x="8.1" y="17.1" width="7.8" height="4.15" rx="1.25" />
        <path d="M12 18.55v1.2" />
      </template>
      <template v-else-if="name === 'help'">
        <circle cx="12" cy="12" r="9" />
        <path d="M9.7 9.35a2.45 2.45 0 1 1 3.35 2.27c-.8.34-1.05.77-1.05 1.58v.45M12 17.25h.01" />
      </template>
      <template v-else-if="name === 'headset'">
        <path d="M4.1 13v-1.5a7.9 7.9 0 0 1 15.8 0V13" />
        <path d="M4.1 12.35H5.6c.85 0 1.55.7 1.55 1.55v3.05c0 .85-.7 1.55-1.55 1.55H4.1zM19.9 12.35h-1.5c-.85 0-1.55.7-1.55 1.55v3.05c0 .85.7 1.55 1.55 1.55h1.5zM16.85 18.1c-.55 1.8-1.75 2.55-3.65 2.55H12" />
      </template>
      <template v-else-if="name === 'lock'">
        <rect x="4.75" y="9.5" width="14.5" height="11.25" rx="2" />
        <path d="M8 9.5V7a4 4 0 0 1 8 0v2.5M12 14v2.4" />
      </template>
      <template v-else-if="name === 'user'">
        <circle cx="12" cy="7.15" r="4" />
        <path d="M4.35 20.35c.28-4 3.05-6.55 7.65-6.55s7.37 2.55 7.65 6.55" />
      </template>
      <template v-else-if="name === 'home'">
        <path d="m3.7 10.15 8.3-6.65 8.3 6.65M5.2 9.2v10.55c0 .41.34.75.75.75H9.5v-6h5v6h3.55c.41 0 .75-.34.75-.75V9.2" />
      </template>
      <path v-else-if="name === 'chevron'" d="m9 5.5 6 6.5-6 6.5" />
      <path v-else-if="name === 'check'" d="m5.5 12.25 4.05 4.05L18.8 7" />
      <path v-else-if="name === 'close'" d="m6 6 12 12M18 6 6 18" />
      <template v-else-if="name === 'clock'">
        <circle cx="12" cy="12" r="9" /><path d="M12 7.25v5.25l3.5 2" />
      </template>
      <template v-else-if="name === 'camera'">
        <path d="M4 7.5h3l1.35-2h7.3l1.35 2h3v11H4z" /><circle cx="12" cy="13" r="3.3" />
      </template>
      <circle v-else cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" />
    </svg>
  </view>
</template>

<style scoped>
.app-icon {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: currentColor;
  line-height: 1;
}

.app-icon svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}
</style>
