# Swordfish

![Swordfish logo](icons/icon.png)

一款基于 XLIFF 标准的高级计算机辅助翻译（CAT）工具，支持 MS Office、DITA、HTML 及其他文档格式。

Swordfish 使用翻译记忆库（TM）和机器翻译（MT），支持片段过滤、术语管理、自定义等多种功能。

## Swordfish 视频教程
- [从源码构建 Swordfish](https://youtu.be/VQveu4BLElE)
- [使用 AI 提示对话框翻译片段](https://youtu.be/8S420n2QieM)
- [通过 AI 菜单或快捷键翻译片段](https://youtu.be/FwsFZCjUajU)

## 许可协议

Swordfish 提供两种使用模式：

- 源代码版
- 年度订阅（含安装包和技术支持）

### 源代码版

Swordfish 的源代码是免费的。任何人都可以免费下载、编译、修改和使用，需遵守随附的许可条款。

你可以在 [Maxprograms Support](https://groups.io/g/maxprograms/)（Groups.io 社区）订阅并请求源代码版的同行协助。

### 订阅版

通过 [Maxprograms 下载页面](https://www.maxprograms.com/downloads/index.html) 获取的官方安装包版本，可免费试用 30 天并申请评估密钥。

个人订阅密钥可在 [Maxprograms 在线商店](https://www.maxprograms.com/store/buy.html) 购买。订阅密钥不可共享或转移至其他设备。

订阅版包含不限次数的邮件技术支持：[tech@maxprograms.com](mailto:tech@maxprograms.com)

### 差异总结

差异 | 源代码版 | 订阅版
----|:------:|:------:
可用安装包 | 否 | 是
macOS 签名启动器 | 否 | 是
Windows 签名启动器及安装包 | 否 | 是
受限功能 | 无 | 无
技术支持 | [Groups.io](https://groups.io/g/maxprograms/) 同行支持 | - 邮件直达 [tech@maxprograms.com](mailto:tech@maxprograms.com) <br> - [Groups.io](https://groups.io/g/maxprograms/) 同行支持

## 相关项目

- [RemoteTM](https://github.com/rmraya/RemoteTM)
- [OpenXLIFF Filters](https://github.com/rmraya/OpenXLIFF)

## 环境要求

- 编译和构建需 JDK 21 或更高版本。可从 [Adoptium](https://adoptium.net/) 获取。
- 需 Apache Ant 1.10.14 或更高版本。可从 [https://ant.apache.org/](https://ant.apache.org/) 获取。
- 需 Node.js 22.13.0 LTS 或更高版本。可从 [https://nodejs.org/](https://nodejs.org/) 获取。
- 需 TypeScript 5.8.3 或更高版本。可从 [https://www.typescriptlang.org/](https://www.typescriptlang.org/) 获取。

## 构建方法

- 克隆本仓库。
- 设置 `JAVA_HOME` 环境变量指向 JDK 21。
- 运行 `ant` 编译 Java 代码。
- 运行 `npm install` 下载并安装 NodeJS 依赖。
- 运行 `npm start` 启动 Swordfish。

### 构建步骤示例

``` bash
  git clone https://github.com/rmraya/Swordfish.git
  cd Swordfish
  ant
  npm install
  npm start
```

首次编译后，直接运行 `npm start` 即可启动 Swordfish。

---

*脚注：此版本为北京语言大学韩林涛在 Swordfish 开源版本基础上进行定制的版本*
