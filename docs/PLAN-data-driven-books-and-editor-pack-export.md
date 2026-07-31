# FlexiBook 计划：编辑器默认示例 + Mod 数据化书籍注册 + 编辑器导出完整材质包 + 跨 Mod 模板继承

**日期**：2026-07-28 (updated 2026-07-29)  
**状态**：Phase D 资源包导出已实现；**books/contents 拆分已落地**（2026-07-30）

---

## 已完成（核心）

- **数据化主题注册**：`assets/<ns>/flexibook/themes/*.json` + `BookThemeRegistry` + `BookThemeReloadListener`（资源覆盖代码注册）。
- **数据化书籍（拆分，无旧格式兼容）**：
  - `assets/<ns>/flexibook/books/<path>.json` → **仅索引** `BookDefinition`（`content` + `theme` + 可选 `font`）
  - `assets/<ns>/flexibook/contents/<path>.json` → **正文** `AdaptiveBookContent`（title/elements/raw/font）
  - `BookDefinitionRegistry` + `BookContentRegistry` + 统一 `BookContentReloadListener`
  - `FlexiBookAPI.createBookFromDefinition` / `resolveBook` / `registerBookDefinition`
- **原版配套路径**（无需 FlexiBook 专用注册表）：
  - `assets/<ns>/lang/*.json`
  - `assets/<ns>/font/*`（ttf provider）
  - `assets/<ns>/textures/**`
- **编辑器默认对齐 mod 示例**：
  - `defaults.ts` 从 `editor/assets/books/demo_guide.json` + `themes/*.json` 加载（需与 mod `assets/flexibook/flexibook/{books,themes}/` 保持同步）。
  - ContentPanel 有 "重置为 Demo"；ThemePanel 有重置默认 / Contain 示例。
- **编辑器自定义纹理**：
  - ThemePanel 支持为 book 选择本地 PNG，立即用于预览（`customTextures` + `loadImageFile`）。
  - 自动同步 book 尺寸到 theme（`bookTexWidth/Height`）。
  - 导出 JSON 时仍写资源路径；本地文件仅预览。

**当前导出**：单文件 JSON + 完整资源包导出（pack.mcmeta / textures / flexibook/{themes,books}/*.json + HOW_TO_USE.txt）。Electron 支持直接写文件夹，浏览器降级为 zip 下载。

---

## 实现明细

### 资源包导出（已完成）
- [x] 入口：顶栏完整包；各面板分项导出。Theme：**纹理 / 背景** → 导出纹理资源包；**导出主题** → 主题资源包 / 主题 JSON（不再堆在 sticky 底栏）
- [x] 纯函数 `buildResourcePack` + `packFilesToZip`（`editor/src/shared/packExport.ts`）+ `PackParts` 分项
- [x] CustomTexture 补 bytes（loadImageFile）
- [x] Electron IPC：dialog:openDirectory + fs:writePack（安全路径校验）
- [x] 主题路径重写 + 可选书内容 theme 指向 + pack.mcmeta + HOW_TO_USE（中英）
- [x] 浏览器降级 zip 下载；成功提示
- [x] 游戏内发放：`/flexibook give <bookId>` / `list`（权限 2）；创造栏不含自定义数据书

### 模板继承（其他 mod 用法）
- **文档已补充**：见 `docs/API.md` §14.4–14.5（分发方式、依赖、自有物品、Function override、主题引用）。
- API：
  ```java
  ItemStack stack = FlexiBookAPI.createBookFromDefinition(
      ResourceLocation.fromNamespaceAndPath("myguide", "guide")
  );
  ItemStack tweaked = FlexiBookAPI.createBookFromDefinition(
      ResourceLocation.fromNamespaceAndPath("myguide", "guide"),
      c -> c.withThemeId(ResourceLocation.fromNamespaceAndPath("myguide", "main"))
  );
  ```
- `AdaptiveBookContent` 为不可变 record；override 为 `Function` 并返回新实例（已修正原 Consumer 无效问题）。
- 物品用自己 namespace，内容/主题来自包的 namespace。
- 导出结果含 `HOW_TO_USE.txt`（编辑器 Phase D）。

### 后续（非 MVP）
- 编辑器内“项目”管理：多主题 + 多书，统一 namespace 导出。
- 自动提取字面量到 lang。
- ~~自定义 widgets 纹理~~（已移除：翻页用原版 GUI 按钮）。
- 复杂图片规则、字体包。
- 命令/创造直接给“数据书”。

---

## 风险与注意
- 自定义背景不影响布局，仅视觉。
- namespace 冲突提醒。
- 资源包定义会覆盖同 id 的代码注册（主题/书籍一致策略）。
- 向后兼容：直接设 DataComponent 的用法不受影响。

---

## 文件变更（概要）
**Mod**：BookContent*、API（含 Function override）、ClientModEvents listener — 已实现。
**Editor**：defaults、ThemePanel 纹理选择、`packExport.ts`、ThemePanel 导出 UI、Electron `openDirectory` + `fs:writePack` — 已实现。
**资源同步**：保持 `editor/assets/` 与 mod `assets/flexibook/flexibook/{books,themes}/` 一致（手动或脚本）。

---

**验收**：
- [x] 编辑器导出输出完整可加载资源包（pack.mcmeta + 自定义/默认纹理 + 重写路径的 theme + 可选 book + HOW_TO_USE）。
- [x] 自动化：`editor` 下 `npm run test:pack`（路径集合 / 纹理改写 / zip / namespace 校验 / 真实 demo_guide）。
- [x] 自动化：Java `PackExportFixtureCodecTest` — 导出 fixture 可被 `BookTheme.CODEC` / `AdaptiveBookContent.CODEC` 解析。
- [x] API override：`Function` + `BookDefinitionApiTest`。
- [ ] 放进游戏后主题/书籍可加载。（需手动验证于 MC 1.21.1 + NeoForge）
- [ ] 其他 mod 用自己物品 ID + 拉取内容正常（客户端目视 / 集成）。
- [ ] 资源重载后一切正确（客户端 F3+T）。

下一步：游戏端手测资源包加载 + 跨模组 API。编辑器 Phase D + codec 自动化已完成。
