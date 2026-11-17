# Spline Roads: Quick Build

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-da8b40)](https://neoforged.net/)

**Spline Roads** 的附属 mod：一个「快速建造工具」，让长距离直路的取点和检查变得可靠。

> 主 mod：[Spline-Roads-1.21.1-NeoForge](https://github.com/xzx576521635-ux/Spline-Roads-1.21.1-NeoForge-)
> 本仓库只包含附属 mod 的代码，**必须**同时安装主 mod。

## 功能

手持「快速建造工具」：

1. **右键地面** → 自动放置第一个道路端点（生存模式消耗 1 个 `road_node`，创造模式豁免）；
2. 走动时实时显示：
   - 起点 → 准星方块（方块中心吸附）的**引导线**，形状跟随主 mod 的道路几何（AUTO 曲线 / 强制直线）；
   - 左下角 HUD：起点/终点坐标、`ΔX/ΔZ`、直线长度、曲线长度、高差、坡度；
3. **再次右键** → 自动放第二个端点，并打开主 mod 原本的道路配置界面（车道、风格、结构等），照常建造；
4. **V 键**（可改）→ 切换 **强制直线 / 自动曲线**：
   - 强制直线时预览是直线，建出来的路也是主 mod 的 `Mode.STRAIGHT`（真直线）；
5. **Shift + 右键空气** → 取消当前选择。

合成（工作台，无序）：

```
splineroads:road_connector    （道路连接器）
splineroads:endpoint_creator  （端点生成器）
minecraft:compass             （指南针）
```

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x（测试于 21.1.248） |
| Java | 21 |
| Spline Roads | 2.0 移植版（v65 或更新） |

## 构建

需要 JDK 21。编译时需要主 mod 的 jar 放在 `libs/`（已在 `.gitignore` 中排除）：

```
libs/splineroads-2.0.jar
```

```bash
gradlew.bat build        # Windows
./gradlew build          # Linux / macOS
```

或使用带备份的脚本（推荐，构建即生成新的 `.qbN` 备份）：

```powershell
.\build.ps1              # 构建 + 备份到 ../mods_backup
.\build.ps1 -Install     # 构建 + 备份 + 安装到 ../mods
```

产物：`build/libs/splinequickbuild-<version>.jar`

## 安装

1. 安装主 mod（`splineroads-*.jar`）与本附属 mod；
2. 两个 jar 都放进 `.minecraft/mods/`；
3. 启动游戏，物品栏「Spline Roads」标签页最下方可以找到快速建造工具。

## 文档


## 许可

- 本附属 mod 代码：MIT（见 `LICENSE`）
- 依赖的主 mod 基于上游 [ShiraAya/Spline-Roads-Release](https://github.com/ShiraAya/Spline-Roads-Release) 的 2.0（MIT）源码，署名归原作者
- 上游 2.1.0 起为自定义非商业许可；本仓库不包含 2.1.0 代码
