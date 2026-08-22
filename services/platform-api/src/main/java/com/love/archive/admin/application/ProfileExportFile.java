package com.love.archive.admin.application;

/** 导出结果：文件名与 CSV 正文（含 UTF-8 BOM）。 */
public record ProfileExportFile(String fileName, String csv) {
}
