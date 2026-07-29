# FlexiBook 内置统一默认字体实施计划

> **状态：已落地（P0）** · 独立文档，勿合并进 todo / DESIGN / 其他 PLAN
>
> 进度快照：
> - [x] `assets/flexibook/font/{default.json,unifont_all-17.0.05.zip,LICENSE-unifont.txt}`
> - [x] `scripts/update-unifont.sh`
> - [x] `FlexiBookFonts` + layout/screen 解析语义（`resolvedFont()`，永不静默 `minecraft:default`）
> - [x] 编辑器 `UnihexFont.ts` + `sync-font-assets.mjs` + App/Preview 接入（无系统 fillText 正文）
> - [x] 测试 / docs（API §12、editor README）/ `./gradlew test` + `npm run test:font`
>
> 下文即实施规格；改计划只改本文件。

## Context（问题背景；实施前）

实施前：模组在书未声明 `defaultFont` 时退回 `minecraft:default`，编辑器则用 `ascii.png + 浏览器 Unifont OTF` 近似；两端不是同一字体提供器，导致字宽、粗体 offset、换行、分页和页码居中无法严格同步。

目标（已达成）：让**书内所有默认文字**（标题、正文、标题元素、列表符号、空书提示、页码）使用 FlexiBook 自带的同一份位图字形数据，不再依赖 Minecraft 默认字体；编辑器直接解析同一资源并复刻 Minecraft 1.21.1 的字形度量。

选用 Minecraft 1.21.1 原生 `unihex` provider（非 OTF）：游戏和编辑器都消费 GNU Unifont 17.0.05 的同一 ZIP/HEX 位图。搜索框、按钮和 tooltip 等普通游戏 UI 不参与书籍排版，继续使用 Minecraft UI 字体。

## 1. 建立唯一字体资源

- 以 GNU Unifont 17.0.05 官方 `unifont_all-17.0.05.hex` 为源，生成 Minecraft `unihex` 所需 ZIP；保留校验值和可重复更新脚本。
- 模组作为唯一资源源：
  - `src/main/resources/assets/flexibook/font/default.json`
  - `src/main/resources/assets/flexibook/font/unifont_all-17.0.05.zip`
  - `src/main/resources/assets/flexibook/font/LICENSE-unifont.txt`
- `default.json` 使用：
  1. `space` provider 固定空格 advance=4、ZWNJ=0；
  2. `unihex` provider 指向上述 ZIP；不引用任何 `minecraft:` font/provider/texture。
- 字体 ID 固定为 `flexibook:default`。新增公共常量（建议 `content/FlexiBookFonts.java`）供布局、Screen、Builder 和 API 复用，杜绝散落字符串。
- 增加 `scripts/update-unifont.*` 或等价 Gradle 任务：从官方固定版本下载/校验 `.hex.gz`，打包为 ZIP并同步许可证；不会在普通 build 时联网。

## 2. 模组默认字体语义

- `AdaptiveBookContent.defaultFont` 继续保留为可选的**显式覆盖**，维持数据格式兼容。
- 增加统一解析方法：`explicit defaultFont` 存在则使用它，否则返回 `FlexiBookFonts.DEFAULT`。因此旧书、数据化书和 Builder 新书即使没有 `font` 字段，也自动使用 `flexibook:default`，绝不落到 `minecraft:default`。
- `BookLayoutEngine.java`：
  - cache key 改用“解析后的字体 ID”；
  - `bookFont` 永远是解析结果；
  - 正文、Heading、Bullet marker、空书文字继续复用 `applyBookFont`；行内/Heading 的显式 font 仍优先。
- `McTextMeasurer.java` 保持 `Style.withFont` 流程，但所有默认布局 Style 都必须已附带解析后的 `flexibook:default`。
- `AdaptiveBookScreen.java`：
  - 标题使用解析后的字体，即便内容未显式声明；
  - 页码改为 styled `Component`，使用同一解析字体进行 `font.width` 和 `drawString`；
  - 正文沿用 `RenderedElement.TextLine.style`；
  - 普通 Screen 控件不改变。
- `AdaptiveBookBuilder.java`、`ExampleBooks.java` 与数据书：默认 Builder 语义注明为 `flexibook:default`；模板书显式写入 `"font": "flexibook:default"`，方便导出与排查。
- 删除模板中的 `minecraft:alt` 演示覆盖（Java Builder 与 `demo_guide.json` 同步），避免默认验收书再次混入 MC 字体；API 仍保留显式自定义字体能力。

## 3. 编辑器直接解析同一 Unihex

- 模组字体 ZIP 是源文件；新增 `editor/scripts/sync-font-assets.mjs`，在 `predev`/`prebuild` 自动复制 ZIP、font JSON 和许可证到 `editor/public/assets/flexibook/font/`，并校验 hash，避免手工双份漂移。
- 添加轻量 ZIP 解压依赖（如 `fflate`），新建 `editor/src/shared/UnihexFont.ts`（或将 `McAtlasTextMeasurer` 重构为该实现）：
  - 读取同一 ZIP 内 `.hex`；
  - 解析 8/16/24/32 像素宽、16 像素高的字形；
  - 精确移植 MC 1.21.1 `UnihexProvider` 的左右裁边算法；
  - advance 使用 `floor((right-left+1)/2)+1`；oversample=2；shadow/bold offset=0.5；整串宽度按 `Font.width` 最后 `ceil`；
  - 绘制时将 16px 源位图以 2× oversample 缩到 8 个逻辑像素；粗体重绘同一字形于 `x+0.5`，italic/underline 对齐 MC `BakedGlyph`/`Font`；
  - 缺字使用字体自身 U+FFFD，不回退浏览器字体或 Minecraft 资源。
- 移除默认路径对以下资源/逻辑的依赖：
  - `editor/assets/minecraft/textures/font/ascii.png`
  - `index.html` 的 `@font-face` Unifont OTF
  - `ensureCjkFontLoaded`、固定 CJK advance、浏览器 `fillText` 字体 fallback。
  旧文件可删除或仅保留为非默认兼容代码，但默认预览不可调用。
- `App.tsx` 在 Unihex ZIP 完成解析后才布局；加载完成增加字体 revision 并清布局缓存，避免先以假宽度布局再跳页。
- `layout.ts` 将缺省 `bookFont` 解析为 `flexibook:default`，cache key 使用解析后的 ID；Heading、正文、bullet 都附带同一 font。
- `PreviewCanvas.tsx`：正文、页码、标题全部通过 Unihex renderer 测宽和绘制；页码继续严格复刻模组逻辑坐标。补上目前编辑器缺失的书标题渲染，使用 `titleOffsetY` 和解析字体与游戏对齐。
- 对显式非 `flexibook:default` 的 font ID：数据仍保留，但编辑器明确显示“不支持的外部字体”并回退预览到 FlexiBook 字体；模板不再包含此类覆盖。该行为写入文档，避免误以为外部资源包字体也能无资产预览。

## 4. 数据、文档与许可证

- 同步修改：
  - `src/main/resources/.../books/demo_guide.json`
  - `editor/assets/books/demo_guide.json` 与 public 镜像（沿用现有同步机制）
  - `docs/API.md` 字体章节
  - `editor/README.md` 与字体 README/NOTICE
- 文档明确优先级：`span/heading 显式 font > book 显式 font > flexibook:default`；缺省不再表示 `minecraft:default`。
- 收录 GNU Unifont 的 OFL/GPL font exception 完整许可和来源/version/hash；项目主许可证不变。

## 5. 自动验证

- Java 单测：
  - 无 `defaultFont` 的内容传入 fake `TextMeasurer` 时，正文/Heading/bullet 的 style/font 必须是 `flexibook:default`；
  - 显式书级字体与行内字体仍按优先级覆盖；
  - cache key 区分解析字体；
  - 页码/标题字体解析辅助逻辑可独立测试。
- Editor 单元/脚本测试：以 ZIP 中固定字形（空格、ASCII、`×`、中文、U+FFFD）断言 bounds、advance、bold advance 和 raster 位图；禁止系统字体 fallback。
- 交叉 golden：相同字符串（普通/粗体/斜体、英文/中文/混合符号）记录 MC Unihex 预期宽度，编辑器结果必须逐项相等；相同 demo 断言页数、行断点和页码 X/Y 一致。
- 构建验证：
  - `./gradlew test build`
  - `npm --prefix editor run build`
  - 检查 JAR 包含 font JSON、Unihex ZIP、许可证且不包含编辑器旧 MC 字体副本。
- 游戏端手测：F3+T 后打开中英模板书，对照编辑器 1x/2x；验证标题、正文、粗体、列表、`×`、中文、页码位置及分页一致；确认未声明 font 的旧书也使用 `flexibook:default`。

## 关键文件

- 模组字体资源：`src/main/resources/assets/flexibook/font/**`
- 默认字体解析：`AdaptiveBookContent.java` / 新 `FlexiBookFonts.java`
- 布局：`layout/BookLayoutEngine.java`, `layout/McTextMeasurer.java`
- Screen：`client/screen/AdaptiveBookScreen.java`
- 模板：`data/ExampleBooks.java`, `.../books/demo_guide.json`
- 编辑器字体：`editor/src/shared/UnihexFont.ts`（替换默认 `McAtlasTextMeasurer` 路径）
- 编辑器整合：`editor/src/renderer/App.tsx`, `PreviewCanvas.tsx`, `editor/src/shared/layout.ts`
- 同步/构建：`editor/scripts/sync-font-assets.mjs`, `editor/package.json`
- 测试/文档：`BookLayoutEngineTest.java`, editor 字体测试脚本, `docs/API.md`, `editor/README.md`

## 落地结果（验收摘要）

| 项 | 状态 |
|----|------|
| 模组 font 资源入 JAR | 是（default.json + zip + LICENSE） |
| `FlexiBookFonts` / `resolvedFont()` | 是 |
| Layout cache key 用解析字体 | 是 |
| Screen 标题 + 页码 styled 字体 | 是 |
| demo_guide / ExampleBooks 无 minecraft:alt 演示 | 是 |
| 编辑器 UnihexFont + sync-font-assets | 是 |
| 默认预览无 fillText / 无 OTF 打包 | 是（legacy 在 `editor/legacy/`） |
| `./gradlew test`（含 3 项 font 解析用例） | 通过 |
| `npm run test:font` + `tsc` + `vite build` | 通过 |
| 游戏内 F3+T 目视对照 | 可选手测（自动化已绿） |

对外说明入口：[`API.md`](./API.md) §12 · [`../editor/README.md`](../editor/README.md) · [`todo.md`](./todo.md)。

## 参考来源

- GNU Unifont 官方主页：https://unifoundry.com/unifont/
- GNU Unifont 17.0.05 构建产物：https://unifoundry.com/pub/unifont/unifont-17.0.05/font-builds/
- GNU Unifont 17.0.05 Plane 0 HEX：https://unifoundry.com/pub/unifont/unifont-17.0.05/font-builds/unifont-17.0.05.hex.gz
