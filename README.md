# 🔍 Tinker's Search

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.18.2-3B8C4A?style=flat-square)](https://www.minecraft.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.2-orange?style=flat-square)](https://www.minecraft.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-purple?style=flat-square)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0-blue?style=flat-square)](LICENSE.txt)

---

## 📖 中文

### 📦 关于

**Tinker's Search** 是一个 **匠魂 (Tinkers' Construct)** 模组的辅助扩展，为冶炼炉界面添加了流体搜索、合金查询与快速交互功能。

### 😊 支持版本

**Tinker's Search** 支持 **匠魂 (Tinkers' Construct)** 的所有版本（包括 1.18.2 / 1.19.2 / 1.20.1）。

### ✨ 功能特性

- 🔎 **流体搜索** - 在冶炼炉界面中快速搜索熔融流体
- 🈶 **拼音搜索** - 支持全拼和首字母搜索（如输入 `tong`、`t` 可匹配「铜」）
- 📑 **三 Tab 页面** - 冶炼炉 / 材料 / 合金，一键切换
- 🧪 **合金查询** - 独立合金 Tab，输入材料名即可查询合金配方、执行次数、后续可延伸合金链，并实时判断当前冶炼炉是否满足条件
- 📚 **全部材料** - 独立材料 Tab，列出所有与合金相关的流体；未在冶炼炉中的流体显示 `locked` 状态，不可移动
- 📋 **卡片式展示** - 清晰展示每种流体的图标、名称和数量
- 🔍 **流体详情页** - 右键流体卡片打开悬浮详情窗口，展示浇筑配方、可制作部件、属性与词条。悬浮窗口不关闭冶炼炉界面，可继续浏览其他流体
- ⭐ **收藏系统** - 卡片右上角星标一键收藏，收藏区独立展示
- 🖱️ **JEI 集成** - 左键/右键点击流体卡片图标可快速查询 JEI 配方/用途（JEI 为可选前置）
- 🔖 **书签支持** - 按 `A` 键将流体添加到 JEI 书签（仅 1.18.2，1.19.2 及 1.20.1 暂不支持）
- 🔄 **一键刷新** - 手动刷新流体列表
- 📜 **滚动查看** - 支持滚动浏览大量流体

### 🎮 操作指南

| 操作 | 功能 |
|---|---|
| **点击左侧 Tab 按钮** | 打开/关闭搜索面板 |
| **点击面板顶部 Tab** | 切换冶炼炉 / 材料 / 合金页面 |
| **搜索框输入** | 按名称或拼音过滤（支持全拼、首字母） |
| **左键点击卡片图标** | 查看 JEI 配方（需要 JEI） |
| **右键点击卡片图标** | 查看 JEI 用途（需要 JEI） |
| **左键点击卡片主体** | 将流体移至冶炼炉底部（`locked` 状态时拦截） |
| **右键点击卡片主体** | 打开流体详情悬浮窗口 |
| **点击卡片右上角星标** | 收藏/取消收藏该流体 |
| **按 `A` 键（鼠标悬停图标时）** | 添加到 JEI 书签（需要 JEI） |
| **详情页滚动 / 翻页 / 搜索** | 浏览更多部件与浇筑配方 |

### ⚙️ 前置要求

| 模组 | 必需 | 说明 |
|---|---|---|
| **Tinkers' Construct** | ✅ 必需 | 核心依赖 |
| **Mantle** | ✅ 必需 | Tinkers' Construct 前置 |
| **JEI (Just Enough Items)** | ❌ 可选 | 提供配方查询和书签功能 |

> 💡 没有 JEI 也能正常使用搜索、拼音、详情页和流体移动功能，只是无法使用 JEI 相关的交互。

### 📥 下载

提供两个版本，功能完全相同，唯一区别是**是否内置拼音支持**：

| 版本 | 文件后缀 | 拼音搜索 | 文件体积     | 推荐场景 |
|---|---|---|----------|---|
| **完整版** | 无后缀 | ✅ | 约 520 KB | 默认选择，开箱即用 |
| **精简版** | `-lite` | ❌ | 约 215 KB | 不想为拼音功能多占体积，且搜索以中文/英文原文字面匹配为主 |

> 💡 **Lite 版本只是不包含拼音搜索功能**，其他所有功能（Tab 切换、合金查询、全部材料、详情页、收藏、JEI 集成、书签等）完全一致。
>
> 💡 完整版内置 **pinyin4j**（Jar-in-Jar 打包），无需单独安装；Lite 版本则不打包 pinyin4j，体积更小。

- **[Modrinth](https://modrinth.com/mod/tinkers-search)**（审核中）
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-search)**
- GitHub Releases

### 🛠️ 开发构建

```bash
git clone https://github.com/leipishu/TinkersSearch.git
cd TinkersSearch

# 构建完整版（含 pinyin4j）
./gradlew build

# 构建 Lite 版（不含 pinyin4j）
./gradlew build -PincludePinyin4j=false
```

构建产物位于 `build/libs/` 目录。

### 📄 许可证

本项目采用 **GNU Lesser General Public License v3.0** 开源协议。

详见 [LICENSE.txt](LICENSE.txt)。

### 🙏 致谢

- **Tinkers' Construct** - 提供冶炼炉 API
- **JEI** - 提供配方查询接口
- **Mantle** - Tinkers' Construct 前置库
- **pinyin4j** - 提供汉字转拼音支持（仅完整版内置）
- 所有反馈问题和提出建议的用户

### 📞 联系方式

- 作者: [Leipishu](https://github.com/leipishu)
- Issue Tracker: [GitHub Issues](https://github.com/leipishu/TinkersSearch/issues)

---

## 📖 English

### 📦 About

**Tinker's Search** is an addon for **Tinkers' Construct** that adds fluid search, alloy query, and quick interaction features to the Smeltery GUI.

### 😊 Supported Versions

**Tinker's Search** supports all versions of **Tinkers' Construct** (including 1.18.2 / 1.19.2 / 1.20.1).

### ✨ Features

- 🔎 **Fluid Search** - Quickly search molten fluids in the Smeltery GUI
- 🈶 **Pinyin Search** - Supports full pinyin and initials (e.g. `tong` or `t` matches "铜")
- 📑 **Three Tabs** - Smeltery / Materials / Alloy, switch with one click
- 🧪 **Alloy Query** - Dedicated Alloy tab: enter a material name to query alloy recipes, craft count, chain results, and real-time feasibility
- 📚 **All Materials** - Dedicated Materials tab listing all alloy-relevant fluids; fluids not in the Smeltery show as `locked` and cannot be moved
- 📋 **Card Display** - Clear display of each fluid's icon, name, and amount
- 🔍 **Fluid Detail Screen** - Right-click a fluid card to open a floating detail window showing casting recipes, craftable parts, stats, and traits. Does not close the Smeltery GUI, allowing you to keep browsing other fluids
- ⭐ **Favorites** - Star icon in the top-right of each card; dedicated favorites section
- 🖱️ **JEI Integration** - Left/right click fluid card icons to view JEI recipes/uses (JEI optional)
- 🔖 **Bookmark Support** - Press `A` to add fluids to JEI bookmarks (only for 1.18.2; 1.19.2 and 1.20.1 not yet supported)
- 🔄 **One-Click Refresh** - Manually refresh the fluid list
- 📜 **Scroll Support** - Scroll through large fluid lists

### 🎮 Controls

| Action | Function |
|---|---|
| **Click left Tab button** | Open/close the search panel |
| **Click top Tab** | Switch between Smeltery / Materials / Alloy |
| **Search box input** | Filter by name or pinyin (full pinyin, initials) |
| **Left click card icon** | View JEI recipe (requires JEI) |
| **Right click card icon** | View JEI uses (requires JEI) |
| **Left click card body** | Move fluid to bottom of Smeltery (blocked when `locked`) |
| **Right click card body** | Open fluid detail window |
| **Click star icon** | Toggle favorite |
| **Press `A` (hovering icon)** | Add to JEI bookmarks (requires JEI) |
| **Detail screen scroll / page / search** | Browse more parts and casting recipes |

### ⚙️ Requirements

| Mod | Required | Note |
|---|---|---|
| **Tinkers' Construct** | ✅ Required | Core dependency |
| **Mantle** | ✅ Required | Tinkers' Construct dependency |
| **JEI (Just Enough Items)** | ❌ Optional | Provides recipe lookup and bookmarks |

> 💡 The mod works fine without JEI for search, pinyin, detail screen, and fluid movement. Only JEI-related interactions are disabled.

### 📥 Download

Two builds are available. They are functionally identical except for **whether pinyin support is bundled**:

| Build | File Suffix | Pinyin Search | File Size | Recommended For |
|---|---|---|-----------|---|
| **Full** | (none) | ✅ | ~520 KB   | Default choice, works out of the box |
| **Lite** | `-lite` | ❌ | ~215 KB   | Users who don't want the extra pinyin size and prefer literal text matching |

> 💡 **The Lite build only omits pinyin search.** Every other feature (Tab switching, alloy query, all materials, detail screen, favorites, JEI integration, bookmarks, etc.) is fully included.
>
> 💡 The Full build bundles **pinyin4j** via Jar-in-Jar — no separate installation needed. The Lite build does not include pinyin4j, resulting in a smaller file size.

- **[Modrinth](https://modrinth.com/mod/tinkers-search)** (Under review)
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-search)**
- GitHub Releases

### 🛠️ Development Build

```bash
git clone https://github.com/leipishu/TinkersSearch.git
cd TinkersSearch

# Build Full (with pinyin4j)
./gradlew build

# Build Lite (without pinyin4j)
./gradlew build -PincludePinyin4j=false
```

Build artifacts are located in `build/libs/`.

### 📄 License

This project is licensed under the **GNU Lesser General Public License v3.0**.

See [LICENSE.txt](LICENSE.txt) for details.

### 🙏 Credits

- **Tinkers' Construct** - Smeltery API
- **JEI** - Recipe lookup API
- **Mantle** - Tinkers' Construct library
- **pinyin4j** - Chinese pinyin conversion support (bundled in Full build only)
- All users who reported issues and suggested features

### 📞 Contact

- Author: [Leipishu](https://github.com/leipishu)
- Issue Tracker: [GitHub Issues](https://github.com/leipishu/TinkersSearch/issues)

---

> ⚠️ **Note**: Tinker's Search and Tinkers' Construct are two separate mods. Please do not confuse them.
>
> ⚠️ **注意**: Tinker's Search 与 Tinkers' Construct 是两个独立的模组，请勿混淆。

---

<p align="center">Made with ❤️ by Leipishu</p>