# 厂商选择与模型目录

设置 → AI 设置 → 选择厂商 → 输入 API Key，停顿约 1.2 秒后自动获取模型。选择模型 → 搜索或浏览 → 点击模型 ID → 保存，再按需测试连接。可关闭自动获取，使用刷新按钮。目录和草稿仅驻留当前页面，不持久化；离开页面取消请求。已有同渠道密钥可留空并手动刷新。

模型目录仅代表服务商返回的候选项，不证明余额、地域、账号权限或所有文本任务均兼容。不会为每个模型自动调用生成接口。没有兼容目录的厂商保留手动模型入口；豆包可能需推理接入点 ID。模型列表按 ID 排序并去重，支持名称搜索，已声明仅输出图像等非文本模态的模型会排除。缺少模态声明时按已知非文本名称过滤，不能保证识别所有未来模型能力。

预设共 14 个，另有自定义渠道。HTTPS、禁止重定向、同域分页、20 页和 10,000 模型上限、单页 4 MiB 限制。认证失败、目录不兼容、网络错误不会展示响应正文或凭据。获取列表不上传学习记录或词库；Key 仅在用户保存时加密写入本机。

百炼同域目录路径由 `/compatible-mode/v1` 转为 `/api/v1/models`，支持 `output.models` 与页码；其他兼容渠道读取 `data[].id`，Claude 使用 `has_more/last_id`。不硬编码随时变动的模型名称。预设地址可编辑，以服务商控制台中的地址及地域为准。

## 官方接口依据

- DeepSeek：https://api-docs.deepseek.com/api/list-models/
- Claude：https://platform.claude.com/docs/en/api/models/list
- Gemini：https://aistudio.google.com/docs/openai#list-models
- 百炼模型目录：https://help.aliyun.com/zh/model-studio/list-models
- 百炼兼容地址：https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope
- 智谱：https://docs.bigmodel.cn/cn/guide/develop/openai/introduction
- MiniMax：https://platform.minimax.cn/docs/api-reference/text-openai-api
- 火山方舟：https://docs.volcengine.com/docs/ark/models-api?lang=zh
- 硅基流动：https://api-docs.siliconflow.cn/docs/api/models-get
- 百度千帆：https://cloud.baidu.com/doc/qianfan-api/s/Amwbl43sp
- 腾讯混元：https://cloud.tencent.com/document/product/1729/111007
- xAI：https://docs.x.ai/developers/rest-api-reference/inference/models
- OpenRouter：https://openrouter.ai/developers
- Kimi：https://platform.moonshot.cn/docs/intro
- OpenAI：https://developers.openai.com/api/reference/resources/models/methods/list

这些是协议/地址依据，不是对每家账号的真实联网成功背书。设备测试覆盖模拟认证、分页、错误、跨域拒绝、密钥隔离、自动加载、搜索选中和保存；未持有各家真实 Key，实际账号应自行测试连接。
