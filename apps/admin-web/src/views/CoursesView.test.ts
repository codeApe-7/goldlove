import { describe, expect, it } from 'vitest'
import coursesSource from './CoursesView.vue?raw'
import layoutSource from '../layouts/AdminLayout.vue?raw'
import routerSource from '../router/index.ts?raw'

/**
 * 源码断言，与 ProfilesView / PaymentSettingsView 同一路子：锁住「决定过一次、
 * 不该被顺手改掉」的地方。切片数学在 utils/videoUpload.test.ts、上传编排在
 * composables/useCourseVideoUpload.test.ts 里都有真单测。
 */
describe('课程管理页', () => {
  it('三种类型的内容要求各自把住', () => {
    expect(coursesSource).toContain('图文与纯文本课程必须填写正文')
    expect(coursesSource).toContain('请先上传视频')
  })

  it('视频时长只登记，界面不做任何范围校验或提示', () => {
    // 用户明确要求：时长只登记，不做任何校验、不做任何提示。
    // 库里也只有一条 `> 0` 的数据合理性 CHECK，没有 5–15 分钟这类业务区间。
    // 这几条断言就是防止有人「顺手补个校验」。
    expect(coursesSource).not.toMatch(/5\s*[-–到]\s*15/)
    expect(coursesSource).not.toContain('时长必须')
    expect(coursesSource).not.toContain('分钟之间')
    expect(coursesSource).not.toContain('时长不在')
    // 时长照原样提交，不夹不改。
    expect(coursesSource).toContain('videoDurationSeconds: form.videoDurationSeconds')
  })

  it('导入 .md 会覆盖正文，覆盖前必须确认', () => {
    // 默默吞掉别人写了一半的稿子是不可接受的。
    expect(coursesSource).toContain('导入会覆盖当前正文，确定继续吗？')
    expect(coursesSource).toContain('if (form.contentMarkdown.trim())')
  })

  it('导入的 md 只进编辑器，保存才入库', () => {
    expect(coursesSource).toContain('Markdown 已导入编辑器，保存后才会入库')
  })

  it('编辑器插图走对象存储，插回正文的是签名地址', () => {
    expect(coursesSource).toContain('@on-upload-img="onUploadImg"')
    expect(coursesSource).toContain('uploadCourseImage(file)')
    expect(coursesSource).toContain('asset.previewUrl')
  })

  it('修改带乐观锁版本', () => {
    // 不带 expectedVersion 后端一律 409，两个运营同时改一门课时后保存的必须撞冲突。
    expect(coursesSource).toContain('expectedVersion: form.expectedVersion')
  })

  it('上传失败时告诉用户重试不会从头再来', () => {
    expect(coursesSource).toContain('重试会从断掉那一块继续')
    expect(coursesSource).toContain('videoUpload.retry()')
  })

  it('取消上传要中止会话', () => {
    // 不中止的话 COS 上会一直躺着半截分块。
    expect(coursesSource).toContain('videoUpload.cancel()')
  })

  it('上传完能就地回放确认，不用先保存再回头检查', () => {
    // 后端在 complete 时一并签了回放地址；新传的与已保存课程的视频走同一个播放器。
    expect(coursesSource).toContain('form.videoPreviewUrl = uploaded.previewUrl')
    expect(coursesSource).toContain('<video')
    expect(coursesSource).toContain('preview-player')
  })

  it('签名地址会过期，界面要说清而不是留个放不出来的播放器', () => {
    expect(coursesSource).toContain('预览地址有效期约 30 分钟')
    expect(coursesSource).toContain('这条视频暂时无法预览')
  })

  it('删除前说清会连带删掉文件', () => {
    expect(coursesSource).toContain('封面与视频文件会一起删除')
  })

  it('关键词只搜标题与副标题', () => {
    // 正文可能上百 KB，全表 LIKE 会把库拖垮；这里的占位符要和后端口径一致。
    expect(coursesSource).toContain('教材名称或副标题')
  })

  it('分块大小由后端下发，前端不自己挑', () => {
    expect(coursesSource).not.toContain('partSizeBytes:')
  })
})

describe('课程路由与菜单', () => {
  it('两个页面都注册了路由', () => {
    expect(routerSource).toContain("path: 'courses'")
    expect(routerSource).toContain("path: 'course-collections'")
    expect(routerSource).toContain('CoursesView.vue')
    expect(routerSource).toContain('CourseCollectionsView.vue')
  })

  it('课程落在运营组里', () => {
    expect(layoutSource).toContain("{ path: '/courses', label: '课程管理', icon: Reading }")
    expect(layoutSource).toContain("{ path: '/course-collections', label: '课程合集', icon: Collection }")
  })
})
