# 服务端兼容说明

本仓库发布的是独立的 QwenPaw 安卓客户端。客户端版本与 [QwenPaw 服务端](https://github.com/agentscope-ai/QwenPaw)版本分别编号；安卓版本号不表示对应同号的服务端版本。

| 安卓公开版 | 适配的 QwenPaw 服务端版本 | 其他服务端版本 |
| --- | --- | --- |
| [0.8.1](https://github.com/Fugitive844/QwenPaw-Android/releases/tag/v0.8.1) | [v2.2.2-beta.3](https://github.com/agentscope-ai/QwenPaw/releases/tag/v2.2.2-beta.3) | 不保证兼容 |

0.8.1 按 QwenPaw v2.2.2-beta.3 的接口与交互适配。服务端升级、降级或使用修改版时，登录、对话、模型、技能、文件及用量等能力可能因接口或权限变化而不同；客户端不会仅凭版本号阻止连接，但这不代表已验证兼容。

后续安卓 Release 应在发布说明中明确对应的 QwenPaw 服务端版本，并同步更新本表。若需要支持其他服务端版本，可以 Fork 本仓库自行适配或提交 Pull Request。
