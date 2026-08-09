export interface SelectedPhoto {
  /** H5 下为原始 File 对象，可直接通过 files 上传 */
  file?: File
  /** 小程序/App 平台的临时文件路径 */
  filePath?: string
}

interface ChooseImageResult {
  tempFiles: Array<File | { path: string }>
}

interface ChooseMediaResult {
  tempFiles: Array<{ tempFilePath: string }>
}

export function hasChooseMediaApi(): boolean {
  return typeof uni.chooseMedia === 'function'
}

function isFileObject(item: File | { path: string }): item is File {
  return typeof File !== 'undefined' && item instanceof File
}

export async function choosePhotos(count: number): Promise<SelectedPhoto[]> {
  if (!hasChooseMediaApi()) {
    // H5 运行时未实现 chooseMedia，必须使用 chooseImage
    const result = (await uni.chooseImage({
      count,
      sourceType: ['album', 'camera'],
    })) as unknown as ChooseImageResult
    const tempFiles = Array.isArray(result.tempFiles)
      ? result.tempFiles
      : [result.tempFiles]
    return tempFiles.map((item) =>
      isFileObject(item) ? { file: item } : { filePath: item.path },
    )
  }

  const result = (await uni.chooseMedia({
    count,
    mediaType: ['image'],
  })) as unknown as ChooseMediaResult
  return result.tempFiles.map((item) => ({ filePath: item.tempFilePath }))
}
