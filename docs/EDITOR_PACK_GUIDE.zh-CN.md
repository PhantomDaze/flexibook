# 编辑器资源包导入 / 导出与游戏内拿书

面向内容作者的逐步说明：在 FlexiBook 编辑器里制作内容、导出 Minecraft 资源包、游戏内启用，并拿到可右键打开的书。

**English:** [`EDITOR_PACK_GUIDE.md`](./EDITOR_PACK_GUIDE.md)。

相关：[编辑器 README](../editor/README.zh-CN.md) · [API §14 数据化书籍](./API.zh-CN.md#14-数据化书籍注册6-段资源布局) · [根 README 中文](../README.zh-CN.md)。

---

## 目录

1. [前置条件](#1-前置条件)
2. [概念（id 与六段包）](#2-概念id-与六段包)
3. [推荐制作流程](#3-推荐制作流程)
4. [导出完整资源包](#4-导出完整资源包)
5. [导出分项资源包](#5-导出分项资源包)
6. [把资源包导回编辑器](#6-把资源包导回编辑器)
7. [游戏内：启用资源包](#7-游戏内启用资源包)
8. [游戏内：拿到书](#8-游戏内拿到书)
9. [其他发书方式（模组 / 代码）](#9-其他发书方式模组--代码)
10. [检查清单](#10-检查清单)
11. [故障排除](#11-故障排除)

---

## 1. 前置条件

| 项 | 说明 |
|----|------|
| FlexiBook 模组 | 客户端已安装（联机若需物品则服务端也要） |
| 编辑器 | `cd editor && npm install`，再 `npm run dev:electron` 或 `npm run dev` |
| Minecraft | **26.2 / 26.1.2 / 1.21.4 / 1.21.1** · NeoForge（亦支持 **1.20.1** Forge，见 `pack_format`） |
| 权限 | `/flexibook` 需要权限等级 **≥ 2**（作弊 / OP） |

编辑器**不在**模组 JAR 内，只产出资源包（以及可选的单文件 JSON）。

---

## 2. 概念（id 与六段包）

### 2.1 导出时填写的三个 id

| 字段 | 示例 | 结果 |
|------|------|------|
| **namespace** | `myguide` | `assets/myguide/…` |
| **themeId** | `main` | `…/flexibook/themes/main.json` → 主题 id `myguide:main` |
| **bookId** | `guide` | `…/books/guide.json` + `…/contents/guide.json` → 书 id **`myguide:guide`** |

游戏内发放：

```text
/flexibook give myguide:guide
```

`namespace:bookId` 是 **books 索引 id**，不是 theme id。

### 2.2 六段包结构

```
{ns}_pack/
  pack.mcmeta
  HOW_TO_USE.txt
  assets/{ns}/
    lang/{en_us,zh_cn,…}.json
    textures/gui/book.png
    textures/item/flexi_book.png          # 可选
    font/…                                # 可选自定义 TTF/OTF
    flexibook/
      themes/{themeId}.json
      contents/{bookId}.json              # 正文
      books/{bookId}.json                 # 仅索引：content + theme + 可选 font
```

| 段 | 路径 | 作用 |
|----|------|------|
| **books** | `flexibook/books/` | 仅索引 |
| **contents** | `flexibook/contents/` | 正文 |
| **themes** | `flexibook/themes/` | 布局 / 颜色 / 书背景 RL |
| **lang** | `lang/` | 原版语言 JSON |
| **fonts** | `font/` | 原版 ttf provider + 文件 |
| **textures** | `textures/` | 书页背景 + 可选物品图标 |

**物品图标：** 物品永远是 `flexibook:flexi_book`。要改创造栏/手上外观，需覆盖  
`assets/flexibook/textures/item/flexi_book.png`（编辑器导出自定义物品图时会写这一份）。

**相对路径基本写死**（namespace 可变）：  
`textures/gui/book.png`、`textures/item/flexi_book.png`。导出时会把主题里的书背景改写成 `{ns}:textures/gui/book.png`。

---

## 3. 推荐制作流程

1. **Theme** — 布局/颜色/imageFit；可选本地 **书背景**、**物品图标**。  
2. **Content** — 结构化元素；正文尽量用**翻译键**（含 `.`、无空格），少用大段字面量。  
3. **Lang** — 添加语言并填键；顶栏语言下拉切换**预览**语言（实时缓存不丢字）。  
4. **Fonts**（可选）— 导入 TTF/OTF，设书级或行内 font。  
5. **预览** — 顶栏切到预览；需要时点 **重新布局**。Scale 1–4 只影响显示。  
6. **导出完整资源包** — 见 [§4](#4-导出完整资源包)。  
7. 放进 `resourcepacks/`、启用、**F3+T**，再 `/flexibook give …` — 见 [§7](#7-游戏内启用资源包)–[§8](#8-游戏内拿到书)。

**草稿：** 主题/正文/翻译/纹理/字体会自动进 IndexedDB。**清草稿**只清缓存，不删已导出的包。

**界面语言 vs 书语言：** 顶栏 **UI**（中文 / English）只改编辑器 chrome；旁边的语言下拉改**书**的译文。

---

## 4. 导出完整资源包

### 4.1 入口

顶栏 → **导出完整资源包…**（主按钮）。

### 4.2 表单字段

| 字段 | 必填 | 说明 |
|------|------|------|
| `namespace` | 是 | 仅 `[a-z0-9_.-]+` |
| `themeId` | 是（默认 `main`） | 主题文件名 / 路径段 |
| `bookId` | 是（默认 `guide`） | 书索引 + 正文文件名 |
| `pack_format` | 默认 `34` | **1.21.1** → **34**；**1.21.4** → **46**；**26.1.2** → **84**；**26.2** → **88**（resource major）；**1.20.1** → **15**。26.x 亦可用 `min_format`/`max_format`。 |

### 4.3 会写出什么

[§2.2](#22-六段包结构) 中适用的全部内容：

- 主题 JSON，`book_texture` 改写为 `{namespace}:textures/gui/book.png`
- 书背景 PNG（本地上传或编辑器默认）
- 若设置了物品图标，会写物品图，并覆盖 `assets/flexibook/textures/item/flexi_book.png`
- contents + books 索引（索引指向 content + theme + 可选 font）
- 全部语言表
- 已导入的自定义字体（id 会按规则改写到包 namespace）

### 4.4 输出位置

| 宿主 | 行为 |
|------|------|
| **Electron** | 选父目录 → 写入 `{namespace}_pack/`（分项导出时后缀不同） |
| **浏览器** | 下载同结构的 **ZIP** |

完整包目录名一般为 `{namespace}_pack`。

### 4.5 导出之后

可看包内 `HOW_TO_USE.txt`，然后按 [§7](#7-游戏内启用资源包) 操作。

---

## 5. 导出分项资源包

同 namespace 的分项包可在游戏里**叠加**启用。只改了一块时用分项更省事。

| 入口（UI） | 内容 | 典型目录名 |
|------------|------|------------|
| Theme → **纹理 / 背景** → **导出纹理资源包…** | `book.png` + 物品图标相关路径 | `{ns}_tex_pack` |
| Theme → **导出主题** → **导出主题资源包…** | 仅 `flexibook/themes/*.json` | `{ns}_theme_pack` |
| Theme → **导出主题** → **导出主题 JSON** | 单文件主题（**不是**资源包） | — |
| Content 底栏 → **导出内容资源包…** | contents + books 索引 | `{ns}_content_pack` |
| Lang → **导出翻译资源包…** | `lang/*.json` | `{ns}_lang_pack` |
| Fonts → **导出字体资源包…** | `font/*.json` + ttf/otf | `{ns}_fonts_pack` |

每个分项包仍带 `pack.mcmeta` 与 `HOW_TO_USE.txt`。

Theme 底栏 sticky 只有打开/保存/重置；分项导出按钮在各自区块里。

游戏内要**读到书**，至少需要：

- `books` + `contents`
- 一个 **theme**（可来自本包或其它包/模组）
- 键对应的 **lang**（否则会显示键名）
- 若依赖自定义书背景，还需要 **textures**

---

## 6. 把资源包导回编辑器

顶栏 → **导入资源包…**

| 宿主 | 来源 |
|------|------|
| Electron | **ZIP** 或 **资源包根目录**（含 `pack.mcmeta` / `assets/`） |
| 浏览器 | **仅 ZIP** |

### 行为

- 有什么加载什么（分项包也可以）。
- 主题、正文、翻译、字体、书背景、物品图标。
- 导出表单默认填入导入的 namespace / themeId / bookId。
- 翻译表**合并**进当前表。
- 字体按 id **合并**。
- 纹理找到则写入对应自定义槽位。

### 提示

- 可只导纹理包改图，不必覆盖正文。  
- 导入后在 Theme / Content / Lang 与预览里核对，再重新导出。

---

## 7. 游戏内：启用资源包

1. 把导出的文件夹或 ZIP 放进实例的 **`resourcepacks`**  
   （开发环境常见：`run/resourcepacks/`）。  
2. 游戏 → **选项 → 资源包** → 移到**右侧已启用**。  
3. 进世界（或仅在菜单完成加载）。  
4. 改包后按 **F3+T** 重载资源。

未启用包时，`/flexibook list` 里不会出现你的 `namespace:bookId`（除非模组 JAR 里自带 bootstrap）。

---

## 8. 游戏内：拿到书

### 8.1 命令（主要方式）

聊天（需作弊/OP）：

```text
/flexibook list
```

应能看到例如 `myguide:guide`，以及内置的 `flexibook:demo_guide`、`fieldnotes:journal` 等。

```text
/flexibook give myguide:guide
```

发给别人：

```text
/flexibook give myguide:guide Steve
```

成功后背包（或满包时掉落）得到带内容组件的 `flexibook:flexi_book`。**右键**打开。

### 8.2 创造栏不会有自定义书

FlexiBook 创造分类只有：

- 空白 `flexi_book`
- 代码内置示例书（Field Guide / demo）

**资源包自定义书不会出现在创造栏。** 请用 `/flexibook give`（或自己的模组代码 — §9）。

### 8.3 id 对照

| 导出时 | 发放命令 |
|--------|----------|
| ns=`note`，bookId=`note11` | `/flexibook give note:note11` |
| ns=`myguide`，bookId=`guide` | `/flexibook give myguide:guide` |
| 模组内置 demo | `/flexibook give flexibook:demo_guide` |

---

## 9. 其他发书方式（模组 / 代码）

内容作者不必写代码；其它模组可用：

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
player.getInventory().add(stack);
```

微调：

```java
ItemStack stack = FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"),
    c -> c.withThemeId(ResourceLocation.fromNamespaceAndPath("myguide", "main"))
);
```

自有物品 + 拉内容：

```java
ItemStack stack = new ItemStack(MyItems.FIELD_GUIDE.get());
AdaptiveBookContent c = FlexiBookAPI.resolveBook(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide")
);
stack.set(ModDataComponents.ADAPTIVE_BOOK_CONTENT.get(), c);
```

详见 [API.zh-CN.md §14](./API.zh-CN.md#14-数据化书籍注册6-段资源布局)。

---

## 10. 检查清单

**编辑器**

- [ ] namespace / themeId / bookId 合法  
- [ ] 正文用翻译键；Lang 表已填  
- [ ] 预览分页正常  
- [ ] 已导出完整包（或分项合计覆盖 books+contents+theme+lang）  
- [ ] 若要自定义物品图，已导出含 item 的纹理包  

**游戏**

- [ ] 已加载 FlexiBook  
- [ ] 资源包在 `resourcepacks/` 且**已启用**  
- [ ] 启用/修改后 **F3+T**  
- [ ] `/flexibook list` 有 `namespace:bookId`  
- [ ] `/flexibook give namespace:bookId`  
- [ ] 右键能打开；主题与译文正确  

---

## 11. 故障排除

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `Unknown FlexiBook id` | 包未启用 / 未重载 / id 错 | 启用包 → F3+T → `list` → 用列表里的完整 id |
| `list` 没有你的书 | 缺 `books/*.json` 或解析失败 | 确认导出含 **content**；books 必须有 `"content"` 字段 |
| 书上显示键名 | 缺 lang 或 ns 不对 | 导出翻译；键与正文一致；启用含 `lang/` 的包 |
| 背景不对/粉黑 | 主题纹理路径 / 纹理包未开 | 启用纹理或完整包；F3+T |
| 物品图标没变 | 未覆盖 `assets/flexibook/textures/item/` | 带自定义 item 导出纹理；确认该包已启用且优先级够 |
| 半透明变成实心 | 旧版未开混合 | 使用当前模组（书 blit 已 `enableBlend`）；PNG 需真 alpha |
| 命令无权限 | 非 OP / 未开作弊 | 权限 ≥ 2 |
| 编辑器导入空 | 不是 FlexiBook 布局 | 需要 `assets/.../flexibook/` 或已知纹理路径 |
| 只开分项包内容空 | 缺 contents 或 books | 再导出 **内容** 包或完整包 |

### 开发环境路径示例

```text
run/resourcepacks/<你的包或 zip>
```

本仓库示例：`run/resourcepacks/note_pack.zip` → id `note:note11`。

---

## 速查卡片

```text
# 编辑器
导出完整资源包…  →  {ns}_pack/
导入资源包…      →  ZIP 或目录

# 游戏
选项 → 资源包 → 启用 {ns}_pack
F3+T
/flexibook list
/flexibook give {ns}:{bookId}
# 右键 flexi_book
```

```java
FlexiBookAPI.createBookFromDefinition(
    ResourceLocation.fromNamespaceAndPath("myguide", "guide"));
```
