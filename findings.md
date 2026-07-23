# 发现与决策

## 需求
- NPC 选择 EF 模型 + 手持武器时，应显示武器的 EF Idle 站姿（如大剑双手持握）
- 退出重进游戏后，站姿应保持不变

## 研究发现

### 问题表现
1. 给 NPC 选择 EF 模型 → 空手 EF Idle 站姿 ✓
2. 给 NPC 武器 → 对应武器 EF Idle 站姿 ✓
3. NPC 击杀目标/魔杖重置 → 正确武器 EF Idle 站姿 ✓
4. **退出重进游戏** → NPC 退化为空手 EF Idle 站姿（武器仍持有但站姿错误）✗

### 当前代码架构（1.21.1）

#### 关键文件
- `MixinEntityNpcInterface.java`: 
  - 在 `addRegularEntries@TAIL` 注入，调用 `modifyLivingMotionByCurrentItem(true)`
  - **仅在服务端执行**（有 `level().isClientSide()` 守卫）
  - 这是负责注册武器动画的唯⼀入口点

- `MixinDataDisplay.java`:
  - `cnpcef$onReadToNBT()`: 从 NBT 读取 efModel，调用 `cnpcef$updateModelCap()`
  - `cnpcef$updateModelCap()`: 创建新 patch，设置到 provider，但**不调用** `modifyLivingMotionByCurrentItem()`

- `CnpcBranchPatchProvider.java`:
  - 使用 EF 默认的 `MobPatchReloadListener.deserializeMobPatchProvider()` 创建标准 `CustomHumanoidMobPatch`

#### 执行流程（退出重进）
1. `Entity.readAdditionalSaveData()` → `DataDisplay.readToNBT()` → `updateModelCap()`
2. `updateModelCap()` 创建新 patch（标准 `CustomHumanoidMobPatch`，无自定义）
3. 服务端: `addRegularEntries()` → `modifyLivingMotionByCurrentItem(true)` ✓
4. 客户端: `addRegularEntries()` **不执行**（被 `isClientSide()` 守卫拦截）✗

### 1.20.1 已修复版本对比

#### 架构差异

| 方面 | 1.20.1（已修复） | 1.21.1（未修复） |
|------|-----------------|-----------------|
| Patch 类 | 自定义 `NpcHumanoidPatch` 继承 `CustomHumanoidMobPatch` | 标准 EF `CustomHumanoidMobPatch` |
| 武器动画方法 | `applyWeaponLivingMotions()` 直接给 animator 注册动画 | `modifyLivingMotionByCurrentItem(true)` 通过 EF 机制 |
| 调用位置 | `MixinDataDisplay.updateModelCap()` **客户端**行 83-84 | `MixinEntityNpcInterface.addRegularEntries()` **仅服务端** |
| Provider | 自定义 `NpcHumanoidPatchProvider` → 创建 `NpcHumanoidPatch` | EF 默认 Provider → 创建标准 `CustomHumanoidMobPatch` |

#### 1.20.1 修复的核心（MixinDataDisplay.java:83-84）
```java
if (npc.level().isClientSide() && newProvider.get() instanceof NpcHumanoidPatch<?> npcPatch) {
    npcPatch.applyWeaponLivingMotions();
}
```
在客户端 `updateModelCap()` 中直接调用 `applyWeaponLivingMotions()`，确保客户端 patch 的武器动画被正确设置。

### 根本原因
`modifyLivingMotionByCurrentItem(true)` 仅在服务端 `addRegularEntries()` 中被调用。客户端 patch 依赖服务端通过 `SPChangeLivingMotion` 数据包同步武器动画。在退出重进场景下，客户端可能无法正确接收/应用此数据包，或客户端 patch 在接收包后被 `updateModelCap()` 替换，导致武器动画丢失。

### EF 关键方法行为
- `modifyLivingMotionByCurrentItem(true)`: 基于当前主手/副手物品，注册 living motion 修改器到 animator。`onStartTracking=true` 时强制应用。服务端调用时会广播 `SPChangeLivingMotion` 包。
- `updateMotion(considerInaction)`: `CustomHumanoidMobPatch` 调用 `commonAggressiveMobUpdateMotion()` 设置 `currentLivingMotion`（IDLE/WALK/CHASE 等），再根据物品使用状态调整 `currentCompositeMotion`。
- `commonAggressiveMobUpdateMotion()`: state.inaction() 时设 `currentLivingMotion = IDLE`（不设 INACTION）

## 技术决策
| 决策 | 理由 |
|------|------|
| 修复方式：在 `MixinDataDisplay.cnpcef$updateModelCap()` 尾部添加 `npc.level().isClientSide() && patch instanceof HumanoidMobPatch` 分支，调用 `modifyLivingMotionByCurrentItem(true)` | 与 1.20.1 已验证方案一致；最小修改；不破坏服务端逻辑 |
| 不创建自定义 patch 类 | 保持 1.21.1 现有架构，使用 EF 标准 `CustomHumanoidMobPatch` |
| 用 try-catch 包裹 `modifyLivingMotionByCurrentItem` 调用 | 防止该调用抛出异常影响 patch 创建流程（非关键路径，nimitz 的谨慎做法） |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| Gradle 构建缓存导致修改不被检测 | 使用 `--rerun-tasks` 强制重新编译 |

## 资源
- `src/main/java/top/bincnpcef/mixin/impl/MixinEntityNpcInterface.java`
- `src/main/java/top/bincnpcef/mixin/impl/MixinDataDisplay.java`
- `src/main/java/top/bincnpcef/common/CnpcBranchPatchProvider.java`
- 参考实现: `开源库及其反编译，参考文件/CNPC-EpicFight-Addon-CE-1.20.1/`

## 视觉/浏览器发现
<!-- 关键：每执行2次查看/浏览器操作后必须更新此部分 -->
<!-- 多模态内容必须立即以文本形式记录 -->

---

*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
