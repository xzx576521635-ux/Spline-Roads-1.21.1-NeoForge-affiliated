# 附属 mod：快速建造（splinequickbuild）

独立 Gradle 工程，位于 `quickbuild/`；硬依赖主 mod `splineroads`，**不修改主 mod**。

## 目标

解决主 mod 建长距离直路时“容易走偏、看不出直不直”的问题：

1. 手持「快速建造工具」右键地面 → 自动放置道路起点节点；
2. 走到下一个位置时，实时渲染一条起点 → 准星方块的引导线；
3. HUD 显示：起点坐标、终点坐标、水平位移（ΔX/ΔZ/直线距离）、高差、坡度百分比；
4. 再次右键 → 打开原版的道路配置界面（车道、风格等），正常点“建造”即可；
5. Shift + 右键空气 → 取消选择。

## 架构

```
quickbuild/
  src/main/java/com/sora/splinequickbuild/
    QuickBuild.java        模组入口：注册物品、加入 splineroads:roads 创造标签
    QuickBuildTool.java    物品：把 use/useOn 转交给主 mod 的 RoadTool
    QuickBuildClient.java  客户端：引导线渲染 + HUD
  src/main/resources/
    META-INF/neoforge.mods.toml   （依赖 splineroads [2.0,)）
    assets/splinequickbuild/...   （lang、物品模型，贴图复用 splineroads:item/endpoint_creator）
```

### 为什么不用改主 mod

`RoadTool.useOn(UseOnContext)` 是 public，且它把所有选择状态写在**当前手持物品栈的 NBT** 里。
附属 mod 的物品只要用同一个上下文回调它：

```java
connector.useOn(new UseOnContext(player, hand, hit));
```

主 mod 就会在「快速建造工具」这个物品栈上写入 `Start` / `Settings` 等数据，
第二次点击时走 `RoadNetwork.open(...)` 打开原本的道路界面。

### 预览的数据来源

- 起点：手持物品 NBT 的 `Start`（BlockPos），再用客户端的 `NodeEntity.node().position()` 取精确坐标；
- 终点：客户端 `mc.hitResult`（方块射线）+ `RoadTool.endpointAtHit(...)`，与服务端放置端点一致；
- 渲染：`RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS` + `RenderType.lines()`；
- HUD：`RenderGuiEvent.Post` + `GuiGraphics.drawString`。

## 构建与备份

```powershell
cd quickbuild
.\build.ps1              # 构建 + 自动生成下一个 .qbN 备份
.\build.ps1 -Install     # 构建 + 备份 + 安装到 mods/
```

约定（与主 mod 一致）：

- 每次构建都生成独立备份 `mods_backup/splinequickbuild-<版本>.jar.qb<N>`，**绝不覆盖**；
- `mods/` 里只保留一个 `splinequickbuild*.jar`；
- 记录写在 `spline_work/快速建造-变更记录.md`（版本表、哈希、回滚、配套主 mod 版本）。

## 当前版本

`0.1.1`（构建序号 **qb2**）：自动放置端点方块 + 引导线 + 数值；还没有方向锁定。

生存模式每次放置端点消耗 1 个 `spline...road_node` 物品，创造模式豁免。

## 待办

- [ ] 方向锁定（按住某键把水平方向锁死，只改长度），进一步解决“走偏”
- [ ] 端点 A/B 的立体标记（十字/方框），比现在的竖线更直观
- [ ] 显示弯道预估长度（AUTO 模式下实际路径可能比直线长）
- [ ] 可选：每次放端点消耗一个物品（生存平衡），创造模式豁免
- [ ] 节点高亮（主 mod 的 `RoadTool.showsNodes` 只认自己的物品类型）
- [ ] 附属 mod 单独建仓库 / 分支，与主 mod 版本对应

## 测试清单

1. 创造模式物品栏 → **Spline Roads** 标签页 → 快速建造工具
   （或 `/give @s splinequickbuild:quick_build_tool`）
2. 右键地面：自动摆出端点方块（生存消耗 1 个 `road_node`），聊天栏提示“已选起点 …”，
   蓝线 + 左上角 HUD 出现
3. 走动 / 看向不同方块：线和数值实时更新
4. 右键第二个位置：弹出道路配置界面，正常建造
5. Shift + 右键空气：取消选择
