# LearnE 英语单词学习项目

## 项目简介

LearnE 是一个英语单词学习应用，采用"最常见释义 + 最常见词组 + 最常见例句"的开发哲学，帮助用户高效记忆单词。

支持多语料库：CET4（大学英语四级）、CATTI（翻译专业资格考试），后续可扩展更多词汇库。

支持微信小程序和 Android 原生应用两种平台。

## 快速启动

### 微信小程序
```bash
# 使用微信开发者工具打开 miniprogram 目录
# 配置 AppID 和云环境
# 编译运行
```

### Android 版本
```bash
# 使用 Android Studio 打开 android 目录
# 同步 Gradle
# 编译: ./gradlew :app:assembleDebug
# 运行项目
```

## 环境要求

| 环境 | 版本 |
|-----|------|
| 微信开发者工具 | 最新稳定版 |
| Android Studio | 4.0+ (AGP 8.5) |
| JDK | 11+ |
| Node.js | 16+（云函数开发） |

## 核心目录结构

```
LearnE/
├── miniprogram/          # 微信小程序
│   ├── pages/            # 页面
│   ├── components/       # 组件
│   ├── utils/            # 工具函数
│   └── config/           # 配置
├── android/              # Android 版本
│   ├── app/src/main/java/com/learne/
│   │   ├── data/         # 数据层 (model, db, repository)
│   │   ├── ui/           # 界面层 (home, learn, listen, challenge, quiz, stats, user, auth, plan)
│   │   └── service/      # 服务层 (AudioPlayer)
│   └── app/src/main/res/
│       ├── layout/       # XML 布局文件
│       └── drawable/     # 图标和图形
├── cloudfunctions/       # 云函数
│   ├── getWords/
│   ├── updateProgress/
│   └── getReviewList/
├── corpora/              # 语料库（多语料库支持）
│   ├── cet4/             # CET4
│   │   ├── data.json
│   │   ├── data.md
│   │   └── audio/
│   ├── catti/            # CATTI
│   │   ├── data.json
│   │   ├── data.md
│   │   └── audio/
├── data/                 # 数据处理
├── scripts/              # 脚本工具
├── docs/                 # 项目文档
└── README.md
```

## 语料库说明

| 语料库 | 词条数 | 音频覆盖 |
|-------|-------|---------|
| CET4 | 3393 | 1000 (29%) |
| CATTI | 4807 | 4626 (100%) |

每个词条包含：单词、音标、词性、释义、词组、词组释义、例句、例句翻译、词频。

## Android 应用流程

```
LoginActivity (admin/admin)
    ↓
StudyPlanActivity (命名计划槽位，可选)
    ↓
MainActivity (planIndex 或 corpusId)
    ↓
├── HomeFragment (主页 - 6种学习模式入口)
│       ↓ 点击模式
│       ├── ChallengeMapFragment (学习地图 - 游戏化组选择，4种状态)
│       │       ↓ 点击某组
│       │       ├── InteractiveLearnFragment (交互学习) → QuizFragment (考试)
│       │       ├── ListenReadFragment (听读模式)
│       │       └── 复习模式/考试模式
│       └── 独立模式（无需地图）
│           ├── DictationFragment (听写模式 - 仅考试通过的单词)
│           ├── FlashcardFragment (闪卡模式 - 仅考试通过的单词)
│           ├── DailyChallengeFragment (每日挑战 - 仅考试通过的单词)
│           ├── StudyStatsFragment (学习统计 - 热力图)
│           └── UserCenterFragment (个人中心)
└── 错题本 / 星标单词入口（学习地图底部按钮）
```

## 学习模式一览

| 模式 | 说明 | 数据来源 |
|------|------|---------|
| **学习模式** | 3步流程：展示→选择题→拼写，完成后可进入考试 | CorpusRepository |
| **听读模式** | 顺序/乱序/循环播放音频，支持盲听和大屏模式 | CorpusRepository |
| **复习模式** | 间隔重复复习，只展示到期需要复习的单词 | ProgressRepository |
| **考试模式** | 全组覆盖考试，交替题型（选择+拼写），80%通过 | CorpusRepository + StudyRepository |
| **听写模式** | 听音频拼写，支持提示系统 | CorpusRepository + StudyRepository |
| **闪卡模式** | 翻转卡片 + 自评掌握度 | ProgressRepository |
| **每日挑战** | 随机10词，3种题型循环（选择+拼写+闪卡） | ProgressRepository + CorpusRepository |

## 游戏化学习地图

ChallengeMapFragment 作为核心任务调度中心：

- **网格地图**（每行5组），4种状态颜色：
  - **红色**（未学习）→ 进入学习
  - **黄色**（已学习未考试）→ 进入考试
  - **橙色**（考试未通过）→ 弹窗选择：再考一次/重新学习
  - **绿色**（考试通过）→ 可重考或复习
- **复习提示 badge**：组按钮上显示待复习单词数量
- **成就系统**：里程碑目标（每10组/50组）、连续学习天数
- **底部入口**：错题本、星标单词

## 记忆系统

### 间隔重复算法（Ebbinghaus）

| Stage | 间隔 | 说明 |
|-------|------|------|
| 0 | 立即 | 新学单词 |
| 1 | 1 天 | 首次复习 |
| 2 | 3 天 | 第二次复习 |
| 3 | 7 天 | 第三次复习 |
| 4 | 15 天 | 最高级，标记为已掌握 |

答对 → stage + 1，按间隔设置下次复习时间
答错 → 重置到 stage 1（1 天后复习）
Stage 4 → `mastered = true`，不再出现在复习列表中

### 数据存储

- **Room Database**：`word_progress` 表存储每个单词的复习进度
- **ProgressDao**：`getWordsForReview()` 查询到期复习的单词
- **UserPreferencesRepository**：组完成状态、考试通过状态、计划进度

## Android 技术架构

- **架构模式**: MVVM (Fragment + ViewBinding + Kotlin Coroutines)
- **导航**: FragmentManager + Back Stack
- **数据库**: Room (AppDatabase v7，含 StarredWord)
- **JSON 解析**: Gson
- **依赖注入**: 手动构造（Repository 层封装）
- **Min SDK**: 24
- **Target SDK**: 34

## Room Database 表结构

| 表名 | 用途 |
|------|------|
| `users` | 用户信息 |
| `word_progress` | 单词进度（stage、掌握状态、下次复习时间） |
| `wrong_words` | 错题本（来源模式标记） |
| `study_records` | 学习记录（日期、学习数、时长） |
| `daily_goal` | 每日目标（打卡、连续天数） |
| `user_notes` | 用户笔记 |
| `achievements` | 成就系统 |
| `study_reminder` | 学习提醒 |
| `listen_history` | 听读历史（进度记录） |
| `corpus_cache` | 语料库缓存 |
| `starred_words` | 星标单词（学习中手动标记"不熟"） |

## 常用命令

```bash
# Android 构建
cd android && ./gradlew :app:assembleDebug

# 语料库格式转换
python3 scripts/convert_json_to_md.py    # JSON → MD
python3 scripts/convert_md_to_json.py    # MD → JSON

# 音频生成
python3 scripts/generate_audio.py        # 批量生成音频
```

## 负责人

- 项目开发：LearnE Team
- 语料库维护：LearnE Team

---

*创建时间: 2026-05-05*
*最后更新: 2026-05-17*