# 🔍 Tinker's Search

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.18.2-3B8C4A?style=flat-square)](https://www.minecraft.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.2-orange?style=flat-square)](https://www.minecraft.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-purple?style=flat-square)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0-blue?style=flat-square)](LICENSE.txt)

---

## 📖 中文

### 📦 关于

**Tinker's Search** 是一个 **匠魂 (Tinkers' Construct)** 模组的辅助扩展，为冶炼炉界面添加了流体搜索与快速交互功能。

### 😊 支持版本

**Tinker's Search** 支持 **匠魂 (Tinkers' Construct)** 的所有版本（包括1.18.2/1.19.2/1.20.1）

### ✨ 功能特性

- 🔎 **流体搜索** - 在冶炼炉界面中快速搜索熔融流体
- 🔎 **合金搜索** - 进入合金查询模式快速搜索流体的合金配方和当前冶炼炉的满足条件
- 📋 **卡片式展示** - 清晰展示每种流体的图标、名称和数量
- 🖱️ **JEI 集成** - 左键/右键点击流体卡片可快速查询 JEI 配方/用途（JEI 为可选前置）
- ⭐ **书签支持** - 按 `A` 键将流体添加到 JEI 书签（仅 1.18.2，1.19.2 及 1.20.1 暂不支持）
- 🔄 **一键刷新** - 手动刷新流体列表
- 📜 **滚动查看** - 支持滚动浏览大量流体

### 🎮 操作指南

| 操作 | 功能 |
|---|---|
| **点击左侧 Tab 按钮** | 打开/关闭搜索面板 |
| **左键点击卡片图标** | 查看 JEI 配方（需要 JEI） |
| **右键点击卡片图标** | 查看 JEI 用途（需要 JEI） |
| **左键点击卡片主体** | 将流体移至冶炼炉底部 |
| **按 `A` 键（鼠标悬停图标时）** | 添加到 JEI 书签（需要 JEI） |
| **搜索框输入** | 按名称过滤流体 |
| **搜索框输入 `/a/`** | 进入合金模式（依然可以在 `/a/` 标签后继续搜索流体） |

### ⚙️ 前置要求

| 模组 | 必需 | 说明 |
|---|---|---|
| **Tinkers' Construct** | ✅ 必需 | 核心依赖 |
| **Mantle** | ✅ 必需 | Tinkers' Construct 前置 |
| **JEI (Just Enough Items)** | ❌ 可选 | 提供配方查询和书签功能 |

> 💡 没有 JEI 也能正常使用搜索和流体移动功能，只是无法使用 JEI 相关的交互。

### 📥 下载

- **[Modrinth](https://modrinth.com/mod/tinkers-search)**（审核中）
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-search)**
- GitHub Releases

### 🛠️ 开发构建

```bash
git clone https://github.com/leipishu/TinkersSearch.git
cd TinkersSearch
./gradlew build
```

构建产物位于 `build/libs/` 目录。

### 📄 许可证

本项目采用 **GNU Lesser General Public License v3.0** 开源协议。

详见 [LICENSE.txt](LICENSE.txt)。

### 🙏 致谢

- **Tinkers' Construct** - 提供冶炼炉 API
- **JEI** - 提供配方查询接口
- **Mantle** - Tinkers' Construct 前置库
- 所有反馈问题和提出建议的用户

### 📞 联系方式

- 作者: [Leipishu](https://github.com/leipishu)
- Issue Tracker: [GitHub Issues](https://github.com/leipishu/TinkersSearch/issues)

---

## 📖 English

### 📦 About

**Tinker's Search** is an addon for **Tinkers' Construct** that adds fluid search and quick interaction features to the Smeltery GUI.

### 😊 Supported Versions

**Tinker's Search** supports all versions of **Tinkers' Construct** (including 1.18.2 / 1.19.2 / 1.20.1).

### ✨ Features

- 🔎 **Fluid Search** - Quickly search molten fluids in the Smeltery GUI
- 🔎 **Alloy Search** - Enter alloy query mode to quickly search for a fluid's alloy recipes and check the current Smeltery's conditions
- 📋 **Card Display** - Clear display of each fluid's icon, name, and amount
- 🖱️ **JEI Integration** - Left/right click fluid cards to view JEI recipes/uses (JEI is optional)
- ⭐ **Bookmark Support** - Press `A` key to add fluids to JEI bookmarks (only for 1.18.2; temporarily not supported for 1.19.2 and 1.20.1)
- 🔄 **One-Click Refresh** - Manually refresh the fluid list
- 📜 **Scroll Support** - Scroll through large fluid lists

### 🎮 Controls

| Action | Function |
|---|---|
| **Click left Tab button** | Open/close the search panel |
| **Left click card icon** | View JEI recipe (requires JEI) |
| **Right click card icon** | View JEI uses (requires JEI) |
| **Left click card body** | Move fluid to bottom of Smeltery |
| **Press `A` (mouse over icon)** | Add to JEI bookmarks (requires JEI) |
| **Search box input** | Filter fluids by name |
| **Search box input `/a/`** | Enter alloy mode (you can continue searching for fluids after the `/a/` tag) |

### ⚙️ Requirements

| Mod | Required | Note |
|---|---|---|
| **Tinkers' Construct** | ✅ Required | Core dependency |
| **Mantle** | ✅ Required | Tinkers' Construct dependency |
| **JEI (Just Enough Items)** | ❌ Optional | Provides recipe lookup and bookmarks |

> 💡 The mod works fine without JEI for search and fluid movement, only JEI-related interactions are disabled.

### 📥 Download

- **[Modrinth](https://modrinth.com/mod/tinkers-search)** (Under review)
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/tinkers-search)**
- GitHub Releases

### 🛠️ Development Build

```bash
git clone https://github.com/leipishu/TinkersSearch.git
cd TinkersSearch
./gradlew build
```

Build artifacts are located in `build/libs/`.

### 📄 License

This project is licensed under the **GNU Lesser General Public License v3.0**.

See [LICENSE.txt](LICENSE.txt) for details.

### 🙏 Credits

- **Tinkers' Construct** - Smeltery API
- **JEI** - Recipe lookup API
- **Mantle** - Tinkers' Construct library
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