<div align=center>
<img src="https://s2.loli.net/2025/05/06/mVL93C6FNhyUSt4.png" style="width:100px;" width="100"/>
<h2>MCLand</h2>
</div>

[项目仓库](https://github.com/Mutantcat-Working-Group/MCLand) · [下载地址](https://github.com/Mutantcat-Working-Group/MCLand/releases) · [问题反馈](https://github.com/Mutantcat-Working-Group/MCLand/issues)

### 一、产品概述

- 公益服「方块猫窝」的服务器脚本，为了让用户玩得放心而整体开源。
- 基于 Spigot 系列插件体系，可参与本项目开发迭代，也可直接用到自己的服务器。
- 版本号三码合一，每个终结版本提供一键启动包，下载即可自主开服。
- **发行方** 由异猫工作群（mutantcat.org）发行，GitHub: https://github.com/Mutantcat-Working-Group

核心价值：服主拿到手的不只是插件，还有一套能直接跑起来的开服方案。

### 二、功能说明

- 主城保护：划定主城安全区，防破坏防骚扰。
- 用户登录：登录验证机制，保障账号归属。
- 定期清理掉落物：定时清理地面掉落物，保持世界整洁与性能。
- 定期清理生物：定时清理闲逛生物，减轻服务器压力。

### 三、安装与下载

最新版本：`1.21.20260920`

从 [Releases](https://github.com/Mutantcat-Working-Group/MCLand/releases) 获取：

| 资产 | 用途 |
| --- | --- |
| `mcland121-1.0.20260920.jar` | 插件本体，放进服务端 `plugins` 目录即用 |
| `MCLand-1.21.20260920.tar.gz` | 一键启动包，解压后即可自主开服 |

另附 `SHA256SUMS`、`README.md`、`LICENSE.txt`。版本号使用「主版本.日期」形式递增（如 `1.21.20260920`），推送同族标签（`v` 前缀可选）后，GitHub Actions 会自动打包并发布 Release。

### 四、快速上手

1. 已有服务器的服主：下载 jar，放入服务端 `plugins` 文件夹，重启服务器即可。
2. 从零开服：下载一键启动包 tar.gz 并解压，按包内说明启动。
3. 注意 Minecraft 自 1.19 起（含服务端）不再支持 Java 8，至少需要 Java 17，历史版本可在 https://jdk.java.net/archive/ 下载。
4. 自行构建服务端可用 Spigot BuildTools：https://hub.spigotmc.org/jenkins/job/BuildTools/，本插件欢迎二次开发。

### 五、版本信息

- MCLand 1.10：已终结
- MCLand 1.21：进行中（当前维护版本）

### 六、开源协议

本项目基于 Apache-2.0 协议开源，许可证见 [LICENSE.txt](LICENSE.txt)。

---

## 致谢

本项目是 [tyza66/MCLand](https://github.com/tyza66/MCLand) 的 Fork，感谢原仓库及其作者的优秀开源工作，本仓库在其基础上继续维护与改进。
