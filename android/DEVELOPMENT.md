# LearnE Android 开发文档

## 架构概述

- **架构模式**: MVVM (Fragment + View Binding + Coroutine)
- **导航结构**: FragmentManager + back stack，无 Navigation Component
- **HomeNavigation**: `HomeFragment` 中定义的对象，提供模式切换回调，由 `MainActivity` 注册处理

## 应用流程

```
LoginActivity (admin/admin)
    ↓
StudyPlanActivity (命名计划槽位，可选)
    ↓
MainActivity (planIndex 或 corpusId)
    ↓
├── ChallengeMapFragment (游戏化地图，4种组状态，planIndex进入)
├── HomeFragment (默认)
│       ↓ 选择模式
│       ├── InteractiveLearnFragment (交互学习，学习阶段错误不记错题本)
│       │       ↓ 学完一组
│       │       └── QuizFragment (组考试，全组覆盖，80%通过)
│       ├── ListenReadFragment (听读模式)
│       ├── DictationFragment (听写模式)
│       ├── FlashcardFragment (闪卡模式)
│       ├── DailyChallengeFragment (每日挑战)
│       └── StudyStatsFragment (学习统计)
└── (ChallengeMapFragment → InteractiveLearnFragment → QuizFragment)
```

## 核心模块

### 1. HomeFragment - 首页（语料库选择 + 模式选择）

**文件**: `app/src/main/java/com/learne/ui/home/HomeFragment.kt`

- 显示已选语料库名称、组大小
- 6种学习模式入口：交互学习、听读、听写、闪卡、每日挑战、学习统计
- "调整学习计划"弹出对话框：选择语料库 + 设置组大小
- 语料库列表通过网络 `CorpusLoader` 加载，失败则使用内置默认（CET4, CATTI）

**HomeNavigation 对象** (定义在同文件中):
```kotlin
object HomeNavigation {
    var startInteractiveLearn: ((String) -> Unit)? = null
    var startListenRead: ((String) -> Unit)? = null
    var startDictation: ((String) -> Unit)? = null
    var startFlashcard: ((String) -> Unit)? = null
    var startDailyChallenge: (() -> Unit)? = null
    var startStudyStats: (() -> Unit)? = null
}
```

### 2. InteractiveLearnFragment - 交互学习模式

**文件**: `app/src/main/java/com/learne/ui/learn/InteractiveLearnFragment.kt`

#### 3步学习流程

| 步骤 | 内容 | 交互 |
|------|------|------|
| Step 1 | 单词展示 | 展示单词、音标、释义、短语、例句，自动播放6段音频 |
| Step 2 | 选择题 | 4选1释义选择，选对自动进入Step3，选错标红1.5s后重试 |
| Step 3 | 填词 | 给出中文释义，输入对应英文单词，提交验证 |

- `Mode` 枚举（公开）: `LEARN`（组内学习）、`REVIEW`（间隔复习）、`REVIEW_DIRECT`（直接复习）、`WRONG`（错题本）
- 每组 `groupSize` 个单词（来自 `UserPreferencesRepository.planGroupSize`）
- 进度通过 `ProgressRepository` 写入 Room DB
- 组完成时通知 `ChallengeMapFragment`（通过 `parentFragmentManager` 查找）
- 会话统计：正确数/错误数/总数，完成时显示统计信息
- 识别"难点单词"（正确率 <50%），完成时展示
- **学习阶段错误不记入错题本**：使用 `saveErrorNoWrong()`（仅记录学习过程反馈）
- **星标功能**：`markUnfamiliar()` → `studyRepo.addStarredWord()`，独立于错题本
- **Guided 模式**（`isGuided=true`）：从 ChallengeMap 进入时隐藏模式标签和组选择器
- **完成引导**：引导模式下学完一组后，显示"开始考试"按钮直接启动 QuizFragment

### 3. ListenReadFragment - 听读模式

**文件**: `app/src/main/java/com/learne/ui/listen/ListenReadFragment.kt`

- 按组展示，可设置组大小
- **播放模式**（3种）:
  - `ORDER`: 顺序播放
  - `SHUFFLE`: 组内乱序播放
  - `LOOP_GROUP`: 组内循环（播完组最后一个单词回到第一个）
- **盲听模式**: 隐藏所有内容，只显示"盲听模式" + "查看内容"按钮，切换后恢复显示
- 控制面板：组选择、播放模式按钮、盲听切换、播放/暂停、上一个/下一个
- 支持跳转全屏模式 (`ListenReadFullScreenActivity`)，传递当前 `playMode`

### 4. DictationFragment - 听写模式

**文件**: `app/src/main/java/com/learne/ui/listen/DictationFragment.kt`

- 播放音频 → 用户输入拼写 → 判对错 → 统计正确率
- **提示系统**: "?"按钮手动获取提示；连续答错2次自动显示首字母提示；连续答错3次显示首尾字母+长度提示
- 组选择下拉框
- 完成时显示统计：正确数、错误数、正确率
- 错误词自动加入错题本 (`StudyRepository`)

### 5. FlashcardFragment - 闪卡模式

**文件**: `app/src/main/java/com/learne/ui/learn/FlashcardFragment.kt`

- 3D翻转动画（`ViewPropertyAnimator.rotationY`）
- 双向切换：英→中 / 中→英（点击切换）
- 自评掌握度：认识（推进stage）/ 模糊（记录不推进）/ 不认识（重置stage + 加入错题本）
- 集成 `ProgressRepository` 间隔重复系统
- 完成时显示统计

### 6. ChallengeMapFragment - 游戏化学习地图

**文件**: `app/src/main/java/com/learne/ui/challenge/ChallengeMapFragment.kt`

- 网格地图（每行5组）：4种状态
  - **未学习**=红色（无图标）→ 进入学习
  - **已学习未考试**=黄色（"→"图标）→ 进入考试
  - **考试未通过**=橙色（"!"图标）→ 弹窗：再考一次/重新学习
  - **考试已通过**=绿色（"✓"图标）→ 弹窗：重考？
- 连续学习天数显示
- 成就系统展示
- 按钮：复习全部、错题模式、旅程重命名
- 导航返回 StudyPlanActivity（`btnBackToPlans`）

### 7. QuizFragment - 组考试（新增）

**文件**: `app/src/main/java/com/learne/ui/quiz/QuizFragment.kt`

- 全组覆盖考试：加载整组所有单词，随机打乱
- **题型交替**: 偶数题=4选1释义选择，奇数题=听音频拼写单词
- 答错 → 调用 `studyRepo.addWrongWord()` 加入错题本（仅考试错误入错题本）
- **通过线**: 正确率 >= 80%
- 完成页：显示正确/错误/正确率 + 错误单词列表
- 通过：标记组为"考试通过"，可返回地图
- 未通过：按钮"再考一次"（打乱重考）或"返回"
- 结果通过 `notifyMapFragment()` 回调 ChallengeMapFragment
- 考试状态存储：`UserPreferencesRepository.markQuizPassed()` / `markQuizFailed()`

### 8. DailyChallengeFragment - 每日挑战

**文件**: `app/src/main/java/com/learne/ui/challenge/DailyChallengeFragment.kt`

- 随机抽取10词（仅从考试通过的组中抽取）
- 三种题型循环：
  1. **选择题**: 英文 → 4选1释义
  2. **拼写题**: 听音频 → 输入单词（显示释义提示）
  3. **闪卡题**: 看英文 → 翻转 → 自评认识/不认识
- 记录连击统计（最高连击）
- 完成时显示结果：正确/错误/正确率/连击

### 9. StudyStatsFragment - 学习统计

**文件**: `app/src/main/java/com/learne/ui/stats/StudyStatsFragment.kt`

- 统计卡片：已学单词数、已掌握数、学习时长（分钟）
- 连续学习天数
- GitHub 风格热力图（近90天），颜色渐变：
  - 无色 → 0词、浅绿 → <5词、中绿 → <10词、深绿 → <20词、最深绿 → 20+词
- 点击热力图格子显示当日学习量 Toast

### 10. UserCenterFragment - 个人中心

**文件**: `app/src/main/java/com/learne/ui/user/UserCenterFragment.kt`

- 显示用户名、学习统计（已学/已掌握/错题数/学习时长/连续天数）
- 能力等级评估：入门/初级/中级/高级（基于掌握率）
- 今日学习总结：今日学习单词数、时长、复习错题数
- 掌握率进度条

### 11. HomeFragment - 首页模式验证

**文件**: `app/src/main/java/com/learne/ui/home/HomeFragment.kt`

- **复习模式**：点击时检查 `reviewWordCount`，为 0 时弹出提示"暂无需要复习的单词"，不进入页面
- **考试模式**：点击时检查 `quizAvailableGroups`（已学习但未考试的组），为空时弹出提示"暂无需要考试的组，请先学习新组"，不进入页面
- **听写/闪卡/每日挑战**：检查 `completedGroupCount`（考试通过组数），为 0 时提示"请先完成考试后再使用"
- 数据通过 `loadReviewAndQuizData()` 异步加载

**HomeNavigation 对象** (定义在同文件中):
```kotlin
object HomeNavigation {
    var startInteractiveLearn: ((String) -> Unit)? = null
    var startListenRead: ((String) -> Unit)? = null
    var startReview: ((String) -> Unit)? = null
    var startQuiz: ((String) -> Unit)? = null
    var startDictation: ((String) -> Unit)? = null
    var startFlashcard: ((String) -> Unit)? = null
    var startDailyChallenge: (() -> Unit)? = null
    var startStudyStats: (() -> Unit)? = null
    var startUserCenter: (() -> Unit)? = null
}
```

### 12. UserPreferencesRepository - 用户偏好

**文件**: `app/src/main/java/com/learne/data/repository/UserPreferencesRepository.kt`

- SharedPreferences 存储
- **听读模式偏好**: `listenPlayMode`（ORDER/SHUFFLE/LOOP_GROUP）、`listenBlindMode`
- **学习计划**: 多计划系统（`PlanSave` JSON 数组），包含名称、语料库、组大小、总词数、总组数、完成组列表、进度
- **组大小预设**: RadioGroup 5个选项（10/20/30推荐/40/50），不可手动输入
- **考试状态追踪**: `getQuizPassedGroups()` / `markQuizPassed()` / `getQuizFailedGroups()` / `markQuizFailed()` / `clearQuizFailed()`（按 corpusId）
- **计划操作**: `createPlan`/`deletePlan`/`loadPlan`/`savePlanProgress`/`renamePlan`/`markGroupCompletedForPlan`
- **组完成追踪**: `getCompletedGroups`/`markGroupCompleted`/`isGroupCompleted`（语料库级别）
- **遗留兼容**: `hasActivePlan`/`planCorpusId`/`planGroupSize`（向后兼容）

### 10. CorpusLoader - 语料库列表加载

**文件**: `app/src/main/java/com/learne/data/repository/CorpusLoader.kt`

- 从 `http://macrossfev.diskstation.me:44000/learne/corpora/list.json` 获取语料库列表
- 失败时返回空列表，调用方使用内置默认值

### 11. CorpusRepository - 词库数据源

**文件**: `app/src/main/java/com/learne/data/repository/CorpusRepository.kt`

- 从 `http://macrossfev.diskstation.me:44000/learne/corpora/{corpusId}/data.json` 加载词库
- HTTP 超时设置：connectTimeout=3s, readTimeout=5s
- `loadWords()` 为 suspend 函数，在 coroutine 中调用
- `getAudioPath()` 根据 word + type 生成音频 URL

### 12. AudioPlayer - 音频播放

**文件**: `app/src/main/java/com/learne/service/AudioPlayer.kt`

- 封装 MediaPlayer，支持异步 prepareAsync
- `play(url, onComplete)` 播放音频，完成后回调 duration
- 支持 `stop()` / `release()` 清理资源

## 数据流

```
LoginActivity → StudyPlanActivity → MainActivity → ChallengeMapFragment/HomeFragment
                                                        ↓
                                            HomeFragment → 选择模式
                                                        ↓
                InteractiveLearnFragment / ListenReadFragment / DictationFragment
                / FlashcardFragment / DailyChallengeFragment / StudyStatsFragment
                                                        ↓
                                              CorpusRepository (network)
                                                        ↓
                                        动态构建 View / 自动播放音频
                                                        ↓
                                    ProgressRepository (写入DB) / StudyRepository (错题记录)
```

## 关键配置

- **主题**: `Theme.MaterialComponents.Light.NoActionBar`
- **ViewBinding**: 已启用
- **Room Database**: 已启用（AppDatabase，version 7，含 StarredWord）
- **Gson**: JSON 解析
- **Kotlin Coroutines**: lifecycleScope + withTimeout
- **核心依赖**: Room, Material Design, Gson, Kotlinx Coroutines

## 构建配置

- **Gradle**: 8.5（使用 wrapper）
- **Min SDK**: 24
- **Target SDK**: 34
- **构建命令**: `./gradlew :app:assembleDebug`

## 已知问题与解决方案

1. **ANR 问题**: Fragment 初始化时不要同步加载网络数据，使用按钮触发异步加载
2. **HTTP 超时**: 设置 connectTimeout=3s, readTimeout=5s，外加 15s coroutine timeout
3. **View 遮挡**: content_area 初始 visibility 设为 GONE，需要时才设为 VISIBLE

## 音频播放流程

### 交互学习模式
Step 1 自动播放6段音频序列，进入Step 2后停止：
1. `words/{word}.mp3`
2. `words/{word}_meaning.mp3`
3. `words/{word}_phrase.mp3`
4. `words/{word}_phrase_meaning.mp3`
5. `words/{word}_example.mp3`
6. `words/{word}_example_meaning.mp3`

### 听读模式
每个单词播放同样的6段音频序列，重复次数可设置（1-5次）：
- **播放按钮**: 按顺序播放6段音频（单词→释义→词组→词组释义→例句→例句释义）
- **暂停按钮**: 暂停当前播放
- **音频间隔**: 每段音频播放完后，停顿与上一次播放相同的时间再播下一段，方便用户跟读
- 可手动暂停/继续、上一个/下一个
- 组切换后自动从该组第一个单词开始

### 大屏模式
- 点击"大屏"按钮进入 `ListenReadFullScreenActivity`
- 全屏沉浸式显示（隐藏状态栏+导航栏）
- 根据当前播放的音频类型动态显示内容：
  - **单词**: 只显示英文单词（大字）
  - **释义**: 显示英文单词 + 中文释义
  - **词组**: 显示"词组"标签 + 英文词组
  - **词组释义**: 显示"词组释义"标签 + 中文释义 + 英文词组
  - **例句**: 显示"例句"标签 + 英文例句
  - **例句释义**: 显示"例句释义"标签 + 中文释义 + 英文例句
- 底部保留播放控制：上一个、暂停/播放、下一个
- 右上角退出按钮返回普通模式

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

## 核心 DAO 查询扩展

- **DailyGoalDao**: `getStreakDays()` - 获取连续打卡天数，`getHeatmapData()` - 获取90天热力图数据
- **StudyRecordDao**: `getHeatmapData(startDate)` - 返回 `HeatmapRecord(date, learnedCount)` 列表
- **ProgressDao**: `getWordsForReview()` - 获取到期复习单词，`getMasteredCount()` - 已掌握数
- **WrongWordDao**: `getWrongWords()` / `markAsCorrected()` / `getByWord()`
- **StarredWordDao**: `getStarredWords()` / `insert()` / `delete()` / `getByWord()`

## 记忆系统功能总结

| 模式 | 核心机制 | 数据来源 |
|------|---------|---------|
| 交互学习 | 3步流程：展示→选择→填词（学习阶段错误不记错题本） | CorpusRepository |
| 组考试 | 全组覆盖考试，交替题型，80%通过，错误入错题本 | CorpusRepository + StudyRepository |
| 听读模式 | 顺序/乱序/循环播放 + 盲听 | CorpusRepository |
| 听写模式 | 听音频→拼写 + 自动提示 + 统计 | CorpusRepository + StudyRepository |
| 闪卡模式 | 翻转卡片 + 自评 + 间隔重复 | ProgressRepository (Ebbinghaus) |
| 每日挑战 | 10词混战（选择+听写+闪卡） | ProgressRepository + CorpusRepository |
| 学习统计 | 热力图 + 统计卡片 + 连续打卡 | Room DB (study_records, daily_goal) |

## 最近开发记录 (2025-06)

### 学习-考试-错题本分离
将学习流程拆分为**学习阶段**和**考试阶段**：
- 学习阶段（InteractiveLearnFragment）：展示→选择→填词，错误不记入错题本
- 考试阶段（QuizFragment，新建）：整组全覆盖考试，错误才入错题本
- 星标功能：`StarredWord` 实体，独立于错题本

### 组大小预设选项
- StudyPlanActivity 新建计划时，组大小改为 RadioGroup 5选（10/20/30/40/50）
- 默认选中 30（推荐），不可手动输入其他数值

### ChallengeMap 4种状态
- 未学习→红色、已学未考→黄色、考试未通过→橙色、考试通过→绿色
- 点击不同状态组触发不同行为（学习/考试/弹窗选择）

### 文件改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/quiz/QuizFragment.kt` | **新建** | 考试流程 |
| `res/layout/fragment_quiz.xml` | **新建** | 考试界面 |
| `ui/challenge/ChallengeMapFragment.kt` | 修改 | 4状态 + 点击逻辑 |
| `ui/learn/InteractiveLearnFragment.kt` | 修改 | 移除学习阶段错题、星标、guided模式、完成引导考试 |
| `ui/plan/StudyPlanActivity.kt` | 修改 | 组大小预设选项 |
| `data/repository/UserPreferencesRepository.kt` | 修改 | 新增考试状态存储方法 |
| `data/repository/StudyRepository.kt` | 修改 | 新增星标方法 |
| `data/model/StudyModels.kt` | 修改 | 新增 StarredWord 实体 |
| `data/db/AllDaos.kt` | 修改 | 新增 StarredWordDao |
| `data/db/AppDatabase.kt` | 修改 | version 6→7，注册 StarredWord |

### 导航入口完善
- ChallengeMapFragment 顶部栏新增"主页"按钮（蓝色），可进入 HomeFragment 访问所有学习模式
- HomeFragment 新增"返回学习地图"按钮，可回到 ChallengeMapFragment
- 导航流程：`Login → StudyPlanActivity → MainActivity(planIndex) → ChallengeMapFragment ↔ HomeFragment → 各学习模式`

### 文件改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/challenge/ChallengeMapFragment.kt` | 修改 | 新增 `navigateToHome()` 方法 + "主页"按钮 |
| `res/layout/fragment_challenge_map.xml` | 修改 | 新增 `btn_home` 按钮 |
| `ui/home/HomeFragment.kt` | 修改 | 新增 `btnBackToMap` 返回地图 |
| `res/layout/fragment_home.xml` | 修改 | 新增 `btn_back_to_map` 按钮 |

### 首页重构 + 个人中心 + 存档删除确认

- 读取存档后进入 HomeFragment（主页），用户自选学习模式
- HomeFragment 改为 2列网格图标+文字布局
- 删除"交互"两字，改为"学习模式"
- 闪卡模式和每日挑战只能基于已学内容（通过 HomeNavigation 回调统一处理）
- 新增"个人中心"模块，查看学习历史、能力水平、今日学习等
- 返回主页不弹出选存档对话框
- 删除存档需手动输入名称确认

### 文件改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `res/drawable/ic_study_mode.xml` | **新建** | 学习模式图标 |
| `res/drawable/ic_listen_read.xml` | **新建** | 听读模式图标 |
| `res/drawable/ic_dictation.xml` | **新建** | 听写模式图标 |
| `res/drawable/ic_flashcard.xml` | **新建** | 闪卡模式图标 |
| `res/drawable/ic_daily_challenge.xml` | **新建** | 每日挑战图标 |
| `res/drawable/ic_wrong_words.xml` | **新建** | 错题本图标 |
| `res/drawable/ic_user_center.xml` | **新建** | 个人中心图标 |
| `res/drawable/ic_learn_map.xml` | **新建** | 学习地图图标 |
| `res/layout/fragment_home.xml` | **重写** | 图标+文字网格布局 |
| `ui/home/HomeFragment.kt` | 修改 | 个人中心按钮、plan菜单 |
| `ui/main/MainActivity.kt` | 修改 | planIndex进入HomeFragment |
| `ui/user/UserCenterFragment.kt` | **新建** | 个人中心 |
| `res/layout/fragment_user_center.xml` | **新建** | 个人中心布局 |
| `ui/plan/StudyPlanActivity.kt` | 修改 | 删除存档需输入名称确认 |
