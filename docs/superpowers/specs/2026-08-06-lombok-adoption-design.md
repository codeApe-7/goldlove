# Lombok 安全适度使用设计

## 目标

在不改变 API、数据库映射和业务行为的前提下，引入 Lombok，减少实体、配置属性与依赖注入构造器中的机械样板代码。

## 使用边界

- MyBatis-Plus 实体使用 `@Getter` 与 `@Setter`，不使用 `@Data`、`@Value` 或类级 `@ToString`。
- 实体中的 `byte[]` 敏感字段继续保留显式 getter/setter，并进行数组复制；Lombok 会跳过已有同名方法。
- 简单 `@ConfigurationProperties` 类使用 `@Getter` 与 `@Setter`；包含防御性复制的 `BrowserSecurityProperties` 保留显式方法。
- 仅构造器负责赋值的 Spring 组件使用 `@RequiredArgsConstructor`。
- 构造器包含密钥解析、参数校验或 dummy Argon2 摘要初始化的安全组件继续使用显式构造器。
- API 请求与响应继续使用 Java `record`，枚举和 Mapper 接口不做无意义改造。
- Lombok 作为编译期可选依赖，不进入可执行包的运行时依赖集合。

## 安全约束

实体不得自动生成 `toString()`，避免密码摘要、初始凭证摘要、手机号密文、微信身份密文或支付备注被意外记录。现有序列化与数据库映射依赖 JavaBean getter/setter，改造后方法签名保持不变。

## 验证

先增加实体行为测试，证明字节数组 getter/setter 仍进行防御性复制；再引入 Lombok 并删除机械访问器。最终运行 Java 25 全量测试、可执行 JAR 打包、依赖检查、Compose 校验与差异检查。
