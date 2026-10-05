# Smart Pickup

智能物品拾取模组（NeoForge 版）。拾取物品优先进入背包、主手/副手物品自动补充、物品拾取黑名单、拾取提示（带物品稀有度颜色）、快捷栏优先白名单。

Smart item pickup mod (NeoForge). Items are picked into your inventory first, held items auto-refill when used up, a blacklist blocks unwanted pickups, pickup toasts show item names with rarity colors, and a hotbar-priority whitelist is supported.

## 功能 Features

1. **智能拾取 Smart pickup** — 拾取物品优先进入背包（同种未满堆优先快捷栏）
2. **自动补充 Auto refill** — 主手/副手物品消耗或损坏时自动补充
3. **拾取黑名单 Blacklist** — 带 UI 的物品拾取黑名单
4. **拾取提示 Pickup toasts** — 自定义 HUD 多条目堆叠显示，按物品稀有度着色（白/黄/青/紫），附魔自动提升稀有度
5. **快捷栏优先白名单 Hotbar-priority whitelist** — 指定物品优先进入快捷栏

## 支持版本 Supported versions

- Minecraft **1.21.1** · NeoForge **21.1.x**
- Mod 版本：**1.0.2**（另有 Fabric 版见 [smartpickup-fabric](https://github.com/Klein-ops/smartpickup-fabric)）

## 安装 Installation

1. 安装 [NeoForge 21.1.x](https://neoforged.net/) for Minecraft 1.21.1
2. 下载 `smartpickup-1.0.2.jar`（Releases）
3. 放入 `mods/` 目录，启动游戏

> 覆盖安装前请删除旧版本的 `smartpickup-*.jar`，避免同 modId 冲突。

## 构建 Build

需要 JDK 21。

```bash
./gradlew build
# 产物在 build/libs/smartpickup-1.0.2.jar
```

## License

MIT
