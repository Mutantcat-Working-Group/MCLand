<div align="center">
<img src="https://s2.loli.net/2025/05/06/mVL93C6FNhyUSt4.png" style="width:100px;" width="100"/>
<h2>MCLand</h2>
</div>

[项目仓库](https://github.com/Mutantcat-Working-Group/MCLand) · [下载地址](https://github.com/Mutantcat-Working-Group/MCLand/releases) · [问题反馈](https://github.com/Mutantcat-Working-Group/MCLand/issues)

- **发行方** 由异猫工作群（mutantcat.org）发行，GitHub: https://github.com/Mutantcat-Working-Group

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
| `mc-1.10` | 1.10 | `1.0.20260920` | 已终结 |
| `mc-26.3` | 26.3 | `26.3.20260929` | 开发中 |
| `mc-26.2` | 26.2 | `26.2.20260929` | 开发中 |

### 三、注意事项

- MC从1.19版本开始（包括服务器）已不再支持Java8至少要用Java17运行
- `mc-26.3`、`mc-26.2` 服务器与插件均基于 Java 25，插件按 Java 25 编译
- `mc-26.3`、`mc-26.2` 分支的 Spigot API 来自 `server.jar` 内置库（`META-INF/libraries/spigot-api-<MC版本>-R0.1-SNAPSHOT.jar`），已分别抽取至 `lib/spigot-26.3.jar`、`lib/spigot-26.2.jar` 作为本地依赖，构建前无需连接 Spigot 仓库
- 注册/登录账号保存在 `plugins/MCLand/MCLand/user/user.db`，按玩家 UUID 建号；Floodgate 默认给基岩版用户名加 `.` 前缀，服内实际名称带前缀，不影响注册登录，修改前缀也不会丢账号
- 这里可以下载到Java的历史版本https://jdk.java.net/archive/
- 新版仓库地址https://github.com/Mutantcat-Working-Group/MCLand
- Spigot构建工具https://hub.spigotmc.org/jenkins/job/BuildTools/

### 四、版本功能

- 主城保护
- 主城保护范围为以出生点为中心的正方形；OP 及持有 `mcland.spawnprotection.bypass` 权限的建造者不受限制，普通玩家不能放置和破坏
- 主城地形对怪物和环境同样封闭：区域内拦截实体爆炸（苦力怕/TNT/恶魂火球/末影水晶）、实体改动方块（末影人搬砖、僵尸破门、劫掠兽啃树叶、牛羊吃草）、液体渗入、点燃燃烧与蔓延生成、活塞推入，以及展示框和画被怪物或爆炸破坏；区域内自有的水池岩浆照常流动，树叶腐烂不受影响
- 主城边界能量墙：把正方形边界渲染成蓝色半透明粒子墙，只发给附近玩家，可在配置中关闭
- 怪物秒杀：怪物进入主城范围会被直接击杀，且不掉落物品和经验；玩家自己在范围内击杀的怪物掉落照常
- 回城与 home（`/spawn` 返回主城，`/sethome` 设置 home，`/home` 返回 home，位置存于 `user.db`）
- 用户注册/登录（`/register`、`/login`、`/password`，SQLite 存储 MD5 密码；未登录玩家仅允许使用 `/register` 和 `/login`，其余命令、聊天和操作会被限制）
 - 玩家传送请求（`/tp 玩家名` 发起，目标玩家 `/accept` 接受，30 秒内有效；管理员保留官方 `/tp` 直接传送）
- `/help` 帮助菜单（列出注册登录、传送、回城 home、主城保护和定期清理功能）
- 定期清理掉落物（每 15 分钟，清理前 60 秒、30 秒、最后 5 秒每秒倒计时播报）
- 定期清理生物（每小时，倒计时播报与掉落物一致；玩家和村民含流浪商人永不清理）
- 动物密度模糊控制（按 100 格见方网格限额，每种动物每格最多保留 2 只，超出移除，不会赶尽杀绝）

### 五、版本号规则

代码版本号格式为 `<MC 版本>.<发布日期>`：前两位注明该插件适配的 MC 版本，后八位是发布日期。例如 `26.2.20260926` 表示适配 MC 26.2、发布于 2026-09-26。

同一 MC 版本分支下多次发版时，只把日期往后（更晚的日期）递增，不回退，以便区分同一分支下的先后发布。版本号体现在以下三处，调整时需保持一致：

- `pom.xml` 的 `<version>`
- `src/main/resources/plugin.yml` 的 `version`
- 发布 tag 形如 `v26.2.20260926`（去掉 `v` 前缀即代码版本号）

`main`（`1.21.20260810`）与 `mc-1.10`（`1.0.20260920`）是历史分支，沿用其既有版本号写法，不做回溯调整；自 2026-09-26 起新增的分支一律按上述格式执行。

### 六、开源协议与致谢

本项目基于 Apache-2.0 协议开源，许可证见 [LICENSE.txt](LICENSE.txt)。

本项目是 [tyza66/MCLand](https://github.com/tyza66/MCLand) 的 Fork，感谢原仓库及其作者的优秀开源工作，本仓库在其基础上继续维护与改进。
