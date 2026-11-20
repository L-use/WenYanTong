# AI 适配规划

0.1.0 尚无网络权限、网络客户端、API 表单或密钥存储；没有任何云端调用。自动、AI、离线仅保存用户偏好，前两者明确显示当前仍查本地。

0.2.0 增加的联网能力仅用于 GitHub APK 更新，不代表 AI 已接入；离线更新检查需要单独确认。学习查询继续使用本地资源。

下一阶段按统一 TranslationResult 与 AiProfile 契约实现 provider 接口。OpenAI-compatible 与 Anthropic Messages 分开适配；实施时核实厂商官方地址、模型 ID、错误码与可用参数。密钥使用 Android Keystore，不写日志、导出或仓库。离线策略在 Repository 入口拦截云端，不能只隐藏 UI。
