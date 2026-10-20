# 快捷跳转 QuickJump

一个纯本地的安卓快递入口小工具：把常用的**取件身份码**和**待取快递**页面做成一键直达的按钮，不用在各个 App 里翻菜单。

> 零权限 · 零网络 · 零数据收集 · 无后端 · 无数据库

## 功能

- **6 个快捷入口**，点击直达对应页面：
  - 菜鸟身份码（菜鸟裹裹）
  - 淘宝身份码 / 淘宝待取快递
  - 拼多多身份码 / 拼多多待取快递
  - 抖音待取快递（兼容正版抖音与抖音极速版）
- **桌面长按菜单**：长按 App 图标，快速使用 3 个身份码入口
- **入口可自定义**：长按任意按钮即可修改该入口的链接，链接失效不用重新打包，保存在本机立即生效
- **多级回退**：每条链接失败会自动降级（换链接 → 拉起目标 App → 网页兜底），尽量保证点得动
- **关于页**：左下角问号，长按可自定义作者介绍内容

## 下载安装

前往 [Releases](https://github.com/chuhe0719/QuickJump/releases) 下载最新 APK，传到手机直接安装（需允许「安装未知应用」）。

## 隐私说明

- 不申请任何权限（无网络、无存储、无定位）
- 不读取短信、通知、相册或账号数据
- 自定义的入口链接仅保存在本机 App 私有目录，不上传
- 代码全部开源，可自行审计

## 实现原理

通过目标 App 公开的 deeplink（`Intent.ACTION_VIEW` + `setPackage` 限定目标包名）跳转到对应内部页面，并对每个入口配置多级回退链。不使用任何非公开 API，也不请求任何系统权限。

## 入口链接失效了怎么办

这些页面入口并非官方开放 API，**菜鸟 / 淘宝 / 拼多多 / 抖音 App 更新后可能失效**。两种修复方式：

1. **免打包修复**：长按失效的按钮，填入新链接即可（新链接可用「开发者助手」类工具查看，或查询 scheme 汇总库）
2. **更新源码**：修改 `MainActivity.kt` 中的 `ENTRIES` 配置后重新构建

不确定哪条链接失效时，打开 **关于（左下角问号）→ 入口体检**：会逐条列出「已安装 / 可用 / 无响应」，检测的是**当前实际生效**的配置（包含你自定义过的链接）。结果可以直接「复制」，或点「上报」自动打开预填好的 Issue（App 自身仍然不联网，由浏览器提交）。

## 本地构建

需要 JDK 17 和 Android SDK 34（仓库已内置 Gradle Wrapper，不需要另外安装 Gradle）：

```
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

需要在 `local.properties` 里指定 SDK 路径（或设置 `ANDROID_HOME` 环境变量）：

```
sdk.dir=/path/to/Android/Sdk
```

推送到 GitHub 后，Actions 会自动构建 debug APK 并作为 artifact 上传（见 `.github/workflows/build.yml`）。
打正式包需要自备 keystore：在 `app/build.gradle.kts` 里补 `signingConfigs`，否则 release 只能使用 debug 签名，换机或换机器重新打包会因签名不一致而无法覆盖安装。

## 声明

- 仅供个人学习与自用，**请勿上架应用商店**
- 各页面入口属于第三方 App 的内部链接，版权归对应公司所有
- 抖音订单页目前没有公开可用的 H5 兜底，因此该入口只走 App 内跳转

## 许可与致谢

- 本项目以 [MIT License](LICENSE) 开源
- 跳转模型参考 [EidenLiu/pickup-code-launcher](https://github.com/EidenLiu/pickup-code-launcher)（MIT），特此致谢

作者：chuhe0719 · 抖音号：29910020595
