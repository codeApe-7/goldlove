package com.love.archive.payment.application;

/**
 * 付款前展示的授权书出站端口。由 consent 模块实现，避免 payment 反向依赖 consent
 * 造成模块环；线上路径与手动路径一样，把用户付款前看到的授权书版本钉在付款记录上。
 */
public interface CurrentAuthorizationDocumentPort {

    /**
     * @return 该版本授权书的主键；版本不存在或未生效时抛出对应的 ApiException
     */
    long requireActiveDocumentId(String version);
}
