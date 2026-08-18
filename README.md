# AI Index Finger

AI Index Finger 是一款本地、由用户主动控制的 Android 工作流自动化工具。它使用无障碍服务执行用户创建的点击、输入、滑动、元素读取、条件分支和有限循环；应用没有网络权限。

## 使用教程

从零创建工作流、配置全部操作和逻辑、检查选择器并调试运行：

**[打开中文图文教程](docs/WORKFLOW_TUTORIAL_ZH.md)**

Need an English quickstart?

**[Open English tutorial](docs/WORKFLOW_TUTORIAL_EN.md)**

教程包含真实简体中文应用截图，以及创建流程、条件逻辑和元素定位策略图。

## 当前版本

- Android 8.0 / API 26 及以上
- 当前测试版本：`0.33.0-beta.10`
- [GitHub Releases](https://github.com/w835041951-dotcom/Ai-Index-Finger/releases)

GitHub Release 中的 debug APK 用于测试和侧载。仓库尚未配置正式发布签名，AAB 不是可直接上传 Google Play 的生产候选。

## 隐私边界

- 不请求网络权限，不包含遥测或云同步。
- 工作流、运行历史和观察到的界面信息保存在本机。
- 自动化只在用户主动运行或调试工作流后执行。
- 调度功能发送本地提醒，不会静默执行自动化。

## 系统闹钟验证脚本

安装系统示例后，Clock 文件夹包含两条用于验证真实自动化链路的脚本：设置验证闹钟时间，以及将验证闹钟铃声设为静音。当前设备验证范围是 API 36 模拟器上的 Google Clock 9.0（包名 `com.google.android.deskclock`），不声明兼容其他 OEM 时钟。

运行前请在 Clock 中准备且只准备一条满足以下条件的专用闹钟：

- 名称必须精确为 `AI_INDEX_FINGER_CLOCK_VALIDATION_V1`；
- 闹钟必须关闭且不设置重复日期；
- 不要把该名称用于个人闹钟。

两条脚本会先验证名称唯一且闹钟已关闭，随后分别把时间设为 10:37、把铃声设为 Silent/静音，并在保存后再次确认闹钟仍处于关闭状态。脚本不会创建、启用或删除闹钟，也不会按列表位置或固定坐标操作。目标缺失、重复或状态不符时会停止。

发布准备与仍需设备验证的项目见 [Google Play 发布计划](docs/STORE_RELEASE_PLAN.md)。