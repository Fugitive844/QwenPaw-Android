# QwenPaw Android

QwenPaw Android 是基于 [QwenPaw](https://github.com/agentscope-ai/QwenPaw) 服务端协议开发的原生安卓客户端。它连接用户自己的 QwenPaw 服务，提供移动端对话、工作区管理和常用配置入口。客户端不包含 QwenPaw 服务端；首次使用时需要填写自己的服务器地址和账号。

支持 Android 8.0（API 26）及以上。公开版包名为 `cn.qwenpaw.android.open`，可以与个人版并存安装。

公开版 **0.8.1**：[下载已签名 APK](https://app.gzsuy.vip/download/releases/24)。下载文件 SHA-256：`203b5409a86d516b0534cba0c278c6c3b9e1683813dfbacb82c228abb12bb084`；签名证书 SHA-256：`5c978fe946796168109911577c3b5ecb85ca4aa947039324dcf203e10615c7ee`。

## 功能

| 范围 | 已实现功能 |
| --- | --- |
| 连接与账号 | 自定义 HTTPS 服务器地址、登录、令牌安全保存、个人资料、修改密码；登录后记住服务器地址，不预置账号或业务服务器。 |
| 对话 | SSE 流式回复、停止生成、重连、历史会话搜索与管理、置顶、归档、重命名、删除、对话内搜索、复制和 Markdown 导出。 |
| 模型与智能体 | 切换工作区与模型；按服务端声明显示思考选项；查看和调整智能体运行配置。 |
| 工具与命令 | 严格、智能、自动、关闭四种工具审批模式；审批操作、工具过程折叠、Loop 与命令面板。 |
| 技能与文件 | 浏览技能及技能池、查看 Markdown 正文、管理配置；选择上传文件，预览或保存对话附件。 |
| 用量 | 查看当前上下文占用、Token 用量和会话缓存命中率；支持压缩上下文。 |
| 应用升级 | 可配置 Zealot 升级源与只读通道；检查版本、下载 APK，并校验 SHA-256、包名和签名后交由系统确认安装。 |

界面使用原生 Android 控件，支持 Markdown、表格、代码块和内置 Mermaid 图表。聊天文件的文本预览由用户主动打开；视频和 HTML 交给系统应用处理。

## 页面截图

截图由项目维护者提供，使用示例界面展示功能。

| 连接与个人中心 | 对话与模型 |
| --- | --- |
| ![连接服务器](docs/img/server-connection.jpg) | ![流式对话](docs/img/chat.jpg) |
| ![个人中心](docs/img/profile.jpg) | ![模型选择](docs/img/model-selection.jpg) |

| 命令与技能 | 配置与用量 |
| --- | --- |
| ![命令面板](docs/img/command-palette.jpg) | ![工具审批模式](docs/img/tool-approval.jpg) |
| ![技能列表](docs/img/skills.jpg) | ![对话选项](docs/img/conversation-options.jpg) |
|  | ![上下文用量](docs/img/context-usage.jpg) |

## 使用与构建

安装公开版 APK 后，先填写 QwenPaw 服务地址并登录。服务器应提供与本客户端兼容的 QwenPaw API；某些能力取决于服务端版本和账号权限。升级通道在正式发布时由发布配置注入。自行从源码构建且未设置通道时，应用不会自动检查更新。

用 Android Studio 打开仓库，配置 Android SDK 35，使用 JDK 17 或 21。命令行可执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

可通过 Gradle 属性 `appEdition`、`appUpdateOrigin`、`appUpdateChannel`、`appDefaultServer` 分别指定版本、升级源、只读通道和默认连接地址。公开源码默认升级源为 `https://app.gzsuy.vip`，默认连接地址与升级通道均为空。签名材料和发布令牌由仓库外的发布配置提供，不应提交到源码仓库。

## 项目关系与许可

本仓库是独立的安卓客户端，基于 [QwenPaw](https://github.com/agentscope-ai/QwenPaw) 的接口与交互实现。QwenPaw 项目使用 Apache-2.0；本仓库原创代码采用 [0BSD](LICENSE)。复用的第三方资源分别遵循其原有许可，详见 [第三方声明](THIRD_PARTY_NOTICES.md) 与 `third_party` 目录。
