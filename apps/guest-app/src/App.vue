<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 应用启动钩子
})
</script>

<style lang="scss">
@use '@/styles/theme.scss';

/* #ifdef H5 */
uni-tabbar {
  display: none !important;
}

.uni-app--showtabbar uni-page-wrapper {
  height: 100% !important;
}

.uni-app--showtabbar uni-page-wrapper::after {
  display: none !important;
}
/* #endif */
</style>
