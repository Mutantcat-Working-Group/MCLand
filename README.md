<div align="center">
<img src="https://s2.loli.net/2025/05/06/mVL93C6FNhyUSt4.png" style="width:100px;" width="100"/>
<h2>MCLand</h2>
</div>

### 一、说明

- 使用在公益服“方块猫窝”上的脚本
- 为了让用户玩的放心，特将服务器脚本开源
- 当然您可以参与本项目的开发迭代或者使用本项目到自己的服务器
- 本服务器插件基于Spigot系列，感谢原作者并支持二次开发
- 版本号三码合一 每个版本在终结版本后会提供一键启动包 直接使用即可自主开服

### 二、分支与版本

不同 MC 版本对应不同分支，每个分支的插件源码直接位于仓库根目录，clone 后 `mvn package` 即可构建。

| 分支 | MC 版本 | 代码版本号 | 状态 |
|---|---|---|---|
| `main` | 1.21 | `1.21.20260810` | 活跃开发 |
| `mc-1.10` | 1.10 | `1.10.20260810` | 已终结 |

### 三、版本号规则

代码版本号格式为 `<MC主版本>.<发布日期>`，例如 `1.21.20260810` 表示 MC 1.21、发布于 2026-08-10。
每次发版时更新日期后缀，便于区分同一 MC 版本下的不同发布。版本号体现在：

- `pom.xml` 的 `<version>`
- `src/main/resources/plugin.yml` 的 `version`

### 三、注意事项

- MC从1.19版本开始（包括服务器）已不再支持Java8至少要用Java17运行
- 这里可以下载到Java的历史版本https://jdk.java.net/archive/
- 新版仓库地址https://github.com/Mutantcat-Working-Group/MCLand
- Spigot构建工具https://hub.spigotmc.org/jenkins/job/BuildTools/

### 四、版本功能

- 主城保护
- 用户登录
- 定期清理掉落物
- 定期清理生物

