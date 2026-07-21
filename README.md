# CNPC Epic Fight Addon Community Edition (CE)
<img width="995" height="195" alt="text3d" src="https://github.com/user-attachments/assets/39c9b2bf-0526-44d2-9c69-b606b9368a25" />

> A community-maintained edition of the original CNPC Epic Fight Addon.

> 原版 CNPC Epic Fight Addon 的社区维护版本。

---

## ✨ Features | 特性

### English

- ✔ Fixes long-standing issues from the original addon.
- ✔ Restores proper Epic Fight stun behavior for NPC attacks.
- ✔ Refactored core architecture for improved stability and maintainability.
- ✔ Compatible with optimization mods such as ModernFix through a universal compatibility layer.
- ✔ Compatible with the Epic Fight addon **Indestructible**.
- ✔ Improves performance while significantly reducing unnecessary log output.
- ✔ Validates malformed data packs, preventing log spam and providing clear in-game error messages.

### 中文

- ✔ 修复官方版本长期存在的多项问题。
- ✔ 修复 NPC 攻击玩家时无法触发 Epic Fight 僵直的问题。
- ✔ 重构核心架构，提高稳定性与可维护性。
- ✔ 通过通用兼容层，兼容以 ModernFix 为代表的底层优化模组。
- ✔ 兼容 Epic Fight 重要附属 **坚不可摧（Indestructible）**。
- ✔ 提升整体性能，并大幅减少无意义日志输出。
- ✔ 对错误数据包进行校验与回滚，避免日志膨胀，并在游戏内提示具体错误位置。

---

# Comparison | 官方版 vs CE

<details>
<summary><b>🗡️ Stun Fix | 僵直修复（点击展开）</b></summary>

### Original
<img width="854" height="487" alt="录制_2026_07_21_22_06_16_325" src="https://github.com/user-attachments/assets/1412cdf9-326b-4d95-b92b-df6d8e678274" />

### Community Edition
<img width="854" height="480" alt="CE-compressed" src="https://github.com/user-attachments/assets/e8b0e199-4c77-4647-9ff4-ee53b5f12d3c" />


NPC attacks correctly apply Epic Fight stun effects.

修复官方版本 NPC 攻击玩家时不会触发 Epic Fight 僵直的问题。

</details>

---

<details>
<summary><b>⚡ Performance | 性能优化（点击展开）</b></summary>

### Original
<img width="1054" height="362" alt="image" src="https://github.com/user-attachments/assets/569e9da4-b5bd-4ddb-99ca-b0bd9b7db6c4" />

### Community Edition
<img width="1042" height="397" alt="image" src="https://github.com/user-attachments/assets/70dd8068-d9c4-4766-abb6-d576c8ada711" />



Reduced unnecessary processing and improved runtime performance.

减少无效计算，提高整体运行效率。

</details>

---

<details>
<summary><b>🛡️ Optimization Mod Compatibility | 优化模组兼容（点击展开）</b></summary>

### Original
<img width="427" height="481" alt="image" src="https://github.com/user-attachments/assets/c4c49283-7cfe-4103-b58a-08b4a7fb027f" />
<img width="531" height="62" alt="image" src="https://github.com/user-attachments/assets/13e715f7-b4de-477d-b6fc-e614aaf05a4d" />

### Community Edition
<img width="428" height="486" alt="image" src="https://github.com/user-attachments/assets/5d616879-aff6-48d0-8193-23224d23cd26" />

A universal compatibility bridge prevents Epic Fight capabilities from becoming invalid when running with optimization mods such as **ModernFix**.
<img width="391" height="55" alt="image" src="https://github.com/user-attachments/assets/2328af74-5a7a-4a35-95d9-10e169e6ce20" />

新增通用兼容桥，可兼容以 **ModernFix** 为代表的大量修改底层实现的优化模组，避免 NPC Epic Fight Capability 失效。

</details>

---

<details>
<summary><b>📜 Logging | 日志优化（点击展开）</b></summary>

### Original

<img width="348" height="390" alt="image" src="https://github.com/user-attachments/assets/7b2b9463-fbe3-415f-8af0-d3b33f9c919a" />

### Community Edition
<img width="348" height="391" alt="image" src="https://github.com/user-attachments/assets/04ecca9d-48f0-42a3-8da7-2e7fa753d9e0" />

Fixed excessive log generation caused by internal logic.

修复内部逻辑导致的日志膨胀问题。

Instead of continuously attempting to load malformed data packs and flooding the log, the Community Edition validates data before loading. Invalid data packs are safely rejected and rolled back, while the exact file and error location are reported directly in the in-game chat, making troubleshooting significantly easier.

不同于官方版会在后台反复加载错误数据包并疯狂刷日志，社区版会在加载前进行数据校验。遇到格式错误的数据包时，将安全拒绝并回滚，同时在游戏聊天栏中直接指出**具体文件**及**错误位置**，便于快速定位和修复问题。

</details>

---

# Improvements

## Community Edition

- Multiple bug fixes
- Internal architecture improvements
- Better compatibility
- Better maintainability
- Better stability
- Performance optimizations

## 社区版改进

- 多项 Bug 修复
- 底层架构重构
- 更好的兼容性
- 更高的可维护性
- 更好的稳定性
- 性能优化
