# 阅闻 · Yuewen

> 一个干净、离线优先的 Android RSS 阅读器。没有账号、没有广告、没有推荐算法，
> 文章只存在你自己的手机上。

<p align="left">
  <img alt="platform" src="https://img.shields.io/badge/Android-8.0%2B-0E9F76?style=flat-square" />
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-4356E0?style=flat-square" />
  <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-D98420?style=flat-square" />
  <img alt="license" src="https://img.shields.io/badge/License-MIT-black?style=flat-square" />
</p>

---

## 这是什么

阅闻是一个**完全本地**的 RSS 新闻阅读器。你把感兴趣的网站订阅进来，它负责抓取、排版、
缓存，让你在一处读完所有内容。

和市面上的资讯 App 相比，它刻意少了三样东西：**登录**、**推荐流**、**埋点上报**。
换来的是一件事 —— 打开就是你要看的东西，读完关掉就行了。

| | |
|---|---|
| **技术栈** | Kotlin + Jetpack Compose（Material 3） |
| **架构** | 离线优先（Offline-First），Room 本地库 + DataStore 偏好 |
| **体积** | release 包约 3.7 MB（开了 R8 混淆与资源裁剪） |
| **最低版本** | Android 8.0（API 26） |
| **代码量** | 74 个 Kotlin 文件，约 1.9 万行 |
| **测试** | 275 条纯 JVM 离线断言，几秒跑完，不需要设备 |

---

## 功能

### 订阅与抓取

- **内置 36 个精选中文源**，分好类（科技 / 财经 / 设计 / 开发 / 国际…），可以整组一键订阅
- **在线搜索免费 RSS**：按名字搜，找到直接订阅
- **按关键词生成专属信息流**：必应 / Google 新闻搜索流，把「关键词」变成一个源
- **OPML 导入导出**：和别的阅读器互通，换 App 不丢订阅
- **RSSHub 订阅**：微博 / 知乎 / B站 / 小红书 / 抖音这些**本来没有 RSS** 的站点也能订进来。
  内置 29 条高频路由模板填个 ID 即可；模板不够用还有「自定义路由」，
  可以覆盖 RSSHub 全部上千条路由；实例地址可切换成自建的
- **订阅源自检**：一键测试所有源，可用的打勾、失效的直接标出来，不用挨个点开试

### 阅读

- **正文自动抽取**：进详情页直接看全文，不用跳浏览器（Readability 算法，Firefox 阅读模式同款思路）
- **排版自定义**：字号四档、行距三档、无衬线 / 衬线可换、三种阅读底色（含米黄纸感和墨夜）
- **双指捏合调字号**：正文里两指一捏就缩放，不用进设置
- **文章大纲**：自动抽取正文小标题成目录，点一下跳到那一节
- **阅读进度记忆**：长文读到一半退出，下次自动回到上次的位置
- **连续阅读**：详情页左右滑动切换上下篇
- **长按摘录 / 写笔记**：正文里长按任意一段即可摘下来，统一在「闻件 → 笔记」里管理

### 朗读（TTS）

- 整篇朗读，**读到哪一段就自动滚到哪一段**
- 切到别的页面声音不会断，顶部会浮出一条「回到文章」
- 通知栏 / 锁屏有控制条：暂停、继续、停止
- 语速四档，朗读过程中改从下一句生效

### 外观

- **八套内置配色**（青绿 / 靛蓝 / 海蓝 / 紫罗兰 / 胭脂 / 琥珀 / 森野 / 石墨），点一下整个界面立刻换色
- **自定义配色**：拖色相（彩虹条）+ 鲜艳度，从你选的颜色实时推出一整套配色
  （背景、卡片、描边都会带上同色系的调子，不是只换一个主色）
- **深浅色自动适配**：每套配色都配了浅色 / 深色两版，按 WCAG AA 校准过对比度
- **三档列表布局**：紧凑 / 卡片 / 杂志，首页右上角随时切
- **可配置的首页**：顶栏按钮、副标题都能单独关掉；筛选维度可选「按分类」「按阅源」或两行叠加
- **首页关键词胶囊**：在设置里填几个自己关心的词（手机 / 汽车 / AI…），
  首页顶部就多出一排胶囊，点一下就只看标题 / 摘要 / 正文含这个词的文章，与分类、阅源三级叠加
- **五种排序**：最新 / 最早 / 随机 / 按阅源 / 按标题
- **桌面图标名可改**：8 个预设名任选（受 Android 限制只能选预设，应用内显示的名字可以随便填）

### 找东西

- **首页直接搜**：顶栏最左边的放大镜点开就是全屏搜索，不用先切到「闻件」再找「搜索」那一栏；
  弹键盘、收键盘、返回都按搜索该有的方式来
- **全文搜索**：标题 / 摘要 / 已抓取的正文 / 来源名都会匹配，不分类别，命中的词在标题里高亮
- **按来源速筛**：搜索结果上方一排来源标签，点一下只看这个源
- **最近搜索**：搜过的词自动记下来，点一下重搜

### 离线与数据

- **离线预加载**：后台把正文提前抓到本机缓存，可勾选「仅 Wi-Fi」；没网时照读全文
- **完整备份 / 恢复**：订阅源 + 收藏 + 笔记 + 个性化设置打包成 JSON，
  换手机一键搬（刻意不含正文缓存，所以文件很小）
- **阅读统计**：累计已读、连续天数、近 7 天柱状图、来源排行、估算阅读时长
- **缓存管理**：图片 / 文章 / 正文三类缓存分开显示占用，想清哪类清哪类

---

## 隐私

这一节很短，因为**这个 App 没有后端**。

- 没有账号系统，不需要注册登录
- 没有埋点、没有统计 SDK、没有崩溃上报
- 订阅列表、收藏、笔记、阅读记录**全部存在本机**（Room 数据库 + DataStore）
- 唯一的网络请求是：抓你订阅的 RSS、抓文章正文、加载文章里的图片

换句话说，卸载 App 就等于删掉全部数据。想换设备就用内置的「备份」导出 JSON。

---

## 构建

### 环境要求

| | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | API 34（compileSdk / targetSdk），最低运行 API 26 |
| Gradle | 8.6（仓库自带 wrapper，不用自己装） |
| Kotlin | 1.9.24（由 Gradle 自动下载） |

### 步骤

```bash
# 1. 克隆
git clone https://github.com/<你的用户名>/yuewen.git
cd yuewen

# 2. 告诉 Gradle 你的 Android SDK 在哪
#    （这个文件被 .gitignore 忽略，每个人的路径不一样，不需要提交）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 3. 构建
./gradlew assembleDebug          # 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # 发布包（开了 R8，体积小很多、也更流畅）

# 4. 装到设备上
./gradlew installDebug
```

Windows 上用 `gradlew.bat` 代替 `./gradlew`。

> **关于 Maven 镜像**：`settings.gradle.kts` 里把腾讯云镜像放在了第一位，
> 这是为了让国内网络能正常拉依赖。国外网络如果觉得慢，把那一行删掉即可，
> 后面还接着 `google()` 和 `mavenCentral()`。

> **关于签名**：仓库里的 `app/build.gradle.kts` 默认复用 Android 的 debug 签名，
> 目的是让 debug 包和 release 包签名一致、可以互相覆盖安装（升级不丢数据）。
> **要正式发布的话，请换成你自己的 keystore** —— 用 debug 签名上架是被应用商店拒绝的。

---

## 离线测试

这个项目最大的工程特点是：**核心逻辑全部抽成了纯 JVM 代码，可以脱离 Android 设备测试**。

```bash
cd NewsApp
bash tools/jvmtest/run.sh            # 只看 PASS/FAIL 摘要
bash tools/jvmtest/run.sh verbose    # 打印每条的详细信息
```

几秒钟出结果，当前 **275 条断言**。覆盖：

| 组 | 内容 |
|---|---|
| RSS 解析 | RSS 2.0 / Atom / RDF 三种格式、CDATA、命名空间、相对路径补全、附件 |
| 正文抽取 | HTML 分块（段落 / 小标题 / 图片）、实体内联、跨段摘录定位 |
| OPML | 导入导出往返、属性大小写、命名空间前缀 |
| 备份 | JSON 导出 / 解析、缺失字段兜底、布尔宽松解析 |
| RSSHub | 路由模板体检、实例地址规范化、参数编码（保留 `/`） |
| 朗读分片 | 按句切分、中英混排、超长句兜底 |
| 阅读统计 | 连续天数、时间窗口、来源聚合 |
| 首页排序 | 五种档位、随机种子稳定性、日期分组开关 |
| **配色生成器** | 全部槽位不透明、色相环扫描、**WCAG 对比度逐对校验**、纯函数可复现 |
| **搜索转义** | LIKE 通配符转义、顺序陷阱、无裸露通配符 |
| **首页关键词** | 清洗规则（去空格 / 忽略大小写去重 / 截断 / 上限）、标题摘要正文三处匹配 |

> 为什么值得这么做：本机没有真机也没有模拟器，Compose 界面没法自动化跑。
> 但「解析对不对」「颜色能不能看清」这些恰恰是最容易出错、也最适合用断言兜住的部分。
> 说实话，配色那组测试第一次跑就抓到了一个真实问题 —— 海蓝配色的深色三级容器
> 文字对比度只有 3.76，低于 AA 标准的 4.5，肉眼看着就是「浅绿字压在深绿上有点糊」。

---

## 项目结构

```
NewsApp/
├── app/src/main/java/com/example/yuewen/
│   ├── MainActivity.kt              # 入口：主题注入、崩溃兜底页、通知权限
│   ├── YuewenApplication.kt         # 全局单例：数据库、仓库、TTS、预加载、Coil
│   ├── data/
│   │   ├── SettingsRepository.kt    # 所有偏好的读写（DataStore）+ 备份白名单
│   │   ├── db/                      # Room 实体、DAO、迁移
│   │   ├── model/                   # 纯数据模型（Article / FeedSource / Note…）
│   │   ├── rss/                     # 抓取、解析、RSSHub 路由、定时刷新
│   │   ├── reader/                  # 正文抽取与富文本分块
│   │   ├── opml/ · backup/          # 导入导出
│   │   └── util/                    # 自建 JSON、SQL LIKE 转义、首页关键词清洗
│   ├── service/                     # 朗读前台服务（MediaStyle 通知）
│   └── ui/
│       ├── theme/                   # 配色生成器（纯 Kotlin）+ 主题注入
│       ├── components/              # 通用组件（卡片、胶囊标签栏、底栏…）
│       ├── screens/                 # 各个页面
│       ├── viewmodel/               # 状态与业务编排
│       └── util/                    # 排序、浏览器选择、TTS 控制台、输入法收起…
└── tools/jvmtest/                   # 离线回归测试（纯 JVM，不需要设备）
```

### 几个刻意的设计决定

**纯逻辑放 `ui/util` 或 `data/`，不放 ViewModel。**
ViewModel 依赖 `android.*`，放进去就永远进不了离线测试。
所以排序、配色生成、TTS 分片、URL 规范化这些都抽成了独立的纯函数文件。

**订阅源 id 由地址派生，不用序号。**
`sourceIdOf(url)` = 地址长度 + hashCode。早期版本用 `s0`/`s1` 这种序号，
增量补种新源时会撞车；id 一重复，列表的 key 就重复，症状是
「点 A 源的操作落到 B 源上」—— 这种 bug 极难查。

**改订阅列表一律走 `settings.mutateSources { }`。**
DataStore 的读写都是异步的，「读出来 → 改 → 写回去」不做互斥的话，
两条路径同时改会互相覆盖，用户看到的是「刚订阅的源凭空消失」。

**配色算法不依赖 Compose。**
`ui/theme/PaletteGen.kt` 全部用 ARGB 的 `Long` 运算，到 `Theme.kt` 才转成 `Color`。
这样「颜色是否合法」「对比度够不够」能在离线测试里几秒钟验完。

---

## 常见问题

<details>
<summary><b>为什么抓不到内容？</b></summary>

先看「阅源 → 我的阅源」里那一行的状态。如果是「不可用」，多半是：

1. **这个源本身挂了** —— 换个源试试；
2. **网络问题** —— 部分源在特定网络环境下会被拦；
3. **RSSHub 公共实例限流** —— 官方实例是免费公用的，抓不到就等一会儿再试，
   或者自己搭一个实例（Docker / Vercel 一键部署），地址填一次就切过去。

</details>

<details>
<summary><b>微博 / 知乎 / 抖音为什么老是失败？</b></summary>

这些平台反爬很强，RSSHub 的路由会随对方改版失效。这是**上游**的问题，不是 App 的。
路由会不定期修复，更新 RSSHub 实例通常就能恢复。

</details>

<details>
<summary><b>文章只有一句话摘要，没有正文？</b></summary>

有些源在 RSS 里只输出摘要（尤其是付费墙站点）。可以：

- 打开「离线阅读 → 自动预加载正文」，App 会去抓原文正文缓存下来；
- 或者点详情页的「原文」直接去浏览器看。

</details>

<details>
<summary><b>桌面图标上的名字改不了？</b></summary>

Android 不允许 App 在运行时改自己的 `android:label`。这个 App 用的是官方给的办法 ——
预置 8 个图标别名（activity-alias），切换启用哪一个。
所以**只能选预设，不能随便填**。应用内显示的名字（首页标题、关于页）没有这个限制。

</details>

<details>
<summary><b>换了手机怎么迁移？</b></summary>

「设置 → 数据 → 备份」，导出一个 JSON 文件，拷到新手机，用「恢复」导入。
订阅源、收藏、笔记、个性化设置全都在里面（正文缓存不含，所以文件很小）。

</details>

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/RSSHub使用教程.html](docs/RSSHub使用教程.html) | 怎么用 RSSHub 订阅微博 / 知乎 / B站 / 小红书；路由模板怎么填、自定义路由怎么拼、自建实例怎么配 |
| [docs/界面设计稿.html](docs/界面设计稿.html) | 早期界面设计稿（浏览器的 HTML 原型） |

## 参与

欢迎提 Issue 和 PR。提交代码前请：

1. 跑一遍 `bash tools/jvmtest/run.sh`，确保 275 条断言全过；
2. 如果你的改动涉及解析、数据层或配色，**顺手补几条断言** —— 那是这个项目最值钱的部分；
3. 保持零编译警告（`./gradlew compileDebugKotlin` 应该只有 `BUILD SUCCESSFUL`）。

---

## 致谢

- [Readability4J](https://github.com/dankito/Readability4J) —— 正文抽取算法
- [jsoup](https://jsoup.org/) —— HTML 解析
- [RSSHub](https://github.com/DIYgod/RSSHub) —— 让没有 RSS 的站点也能订阅
- [Coil](https://coil-kt.github.io/coil/) · [OkHttp](https://square.github.io/okhttp/) —— 图片与网络

## 协议

[MIT](LICENSE) —— 随便用、随便改、随便发，保留版权声明即可。
