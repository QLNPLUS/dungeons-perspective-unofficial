# Upstream Tracking

本项目是原版 Dungeons Perspective 的 Forge 1.20.1 移植版本。原 mod 继续更新时，Forge 分支应以原 mod 为上游来源，按提交逐项迁移，不直接覆盖当前 Forge 代码。

## 当前基线

| 项目 | 内容 |
| --- | --- |
| 上游远程 | `origin` |
| 上游分支 | `origin/1.20.1` |
| Forge 分支 | `forge-1.20.1` |
| 当前共同基线 | `87ff6eaf3f4b95524df5f69884441a1e6f068499` (`Flood Culling`) |
| Forge 移植起点 | `e09925d19c7c041d9a8d6dff9c5fd23629673d22` |
| Minecraft / Forge | `1.20.1` / `47.2.19` |
| Java / Gradle | `17` / `8.12` |
| 记录日期 | `2026-09-15` |

`origin/1.20.1` 是原 mod 的上游跟踪分支。`public/forge-1.20.1` 和 `private/forge-1.20.1` 是本项目的发布或协作远程，不作为原 mod 的更新来源。

## 同步流程

1. 确认工作区没有需要覆盖的用户改动：

   ```bash
   git status --short
   ```

2. 获取上游提交并查看新增内容：

   ```bash
   git fetch origin
   git log --oneline forge-1.20.1..origin/1.20.1
   ```

3. 为一次同步创建临时分支，在临时分支中逐个迁移需要的上游提交：

   ```bash
   git switch -c codex/upstream-sync-YYYY-MM-DD forge-1.20.1
   git cherry-pick -x <upstream-commit>
   ```

4. 如果上游提交使用 Fabric/Yarn API，在同一个 cherry-pick 中解决 Forge 1.20.1 的映射、Mixin 和加载器差异；不要把冲突提交改写成无法追踪来源的手工复制。

5. 每个迁移提交完成后执行编译和客户端烟雾测试。确认所有迁移内容后，再将同步分支合入 `forge-1.20.1`。

6. 同步完成后更新本文件的“同步记录”，记录上游提交、Forge 适配提交和测试结果。

## 提交关系

上游代码、Forge 适配和本地新功能必须保持独立提交：

```text
<上游提交，使用 cherry-pick -x>
forge: adapt upstream <description>
feature: <new feature>
fix: <local fix>
```

不要把本地新功能 squash 到上游迁移提交中。这样以后可以单独判断某个提交是否来自原 mod、是否只属于 Forge 适配，或者是否是本项目自己的功能。

## 迁移判断

- 与加载器无关的逻辑：优先使用 `git cherry-pick -x` 迁移。
- Fabric/Yarn API、Mixin 目标或 Forge 生命周期不同：先 cherry-pick，再在冲突中完成明确的 Forge 适配。
- 只服务于本项目的功能：保留在独立的 `feature:` 提交中，不回写到上游分支。
- 相机、缩放和输入相关的高冲突文件包括 `Mod.java`、`CameraMixin.java`、`MinecraftClientMixin.java` 和 `MouseMixin.java`，迁移这些文件时必须逐段检查本地功能是否仍然存在。

## 同步记录

| 日期 | 上游提交 | Forge 适配 | 测试 |
| --- | --- | --- | --- |
| 2026-09-15 | `87ff6eaf` | `e09925d` 及其后续 Forge 发布提交 | 当前基线已检查；后续同步时补充构建和客户端测试结果 |

