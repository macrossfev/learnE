# LearnE Android 开发文档

## 架构概述

- **架构模式**: MVVM (ViewModel + LiveData + View Binding)
- **导航结构**: FragmentManager + back stack，无 Navigation Component
- **学习模式**: 两种模式 - 交互学习模式（3步流程）和听读模式（卡片自动播放）

## 应用流程

```
MainActivity
    ↓
HomeFragment (语料库选择 + 模式选择)
    ↓
├── InteractiveLearnFragment (交互学习模式)
└── ListenReadFragment (听读模式)
```

## 核心模块

### 1. HomeFragment - 首页（语料库选择 + 模式选择）

**文件**: `app/src/main/java/com/learne/ui/home/HomeFragment.kt`

- 显示已选语料库名称
- 点击"交互学习模式"或"听读模式"进入对应 Fragment
- 点击"更换语料库"弹出对话框选择
- 语料库列表通过网络 `CorpusLoader` 加载，失败则使用内置默认（CET4, CATTI）

### 2. InteractiveLearnFragment - 交互学习模式

**文件**: `app/src/main/java/com/learne/ui/learn/InteractiveLearnFragment.kt`

#### 3步学习流程

| 步骤 | 内容 | 交互 |
|------|------|------|
| Step 1 | 单词展示 | 展示单词、音标、释义、短语、例句，自动播放6段音频 |
| Step 2 | 选择题 | 4选1释义选择，选对自动进入Step3，选错标红1.5s后重试 |
| Step 3 | 填词 | 给出中文释义，输入对应英文单词，提交验证 |

- 通过 `newInstance(corpusId)` 创建，接收语料库ID
- 每组20个单词
- 进度通过 `ProgressRepository` 写入 Room DB

### 3. ListenReadFragment - 听读模式

**文件**: `app/src/main/java/com/learne/ui/listen/ListenReadFragment.kt`

- 通过 `newInstance(corpusId)` 创建，接收语料库ID
- 按组展示（每组50个单词）
- 自动按顺序播放6段音频序列
- **重复次数**: 1-5次可选，每组单词播放完毕后自动进入下一个
- 控制面板：组选择下拉框、重复次数按钮、播放/暂停、上一个/下一个
- 卡片展示：单词+音标+释义、短语+短语释义、例句+例句释义

### 4. UserPreferencesRepository - 用户偏好

**文件**: `app/src/main/java/com/learne/data/repository/UserPreferencesRepository.kt`

- SharedPreferences 存储
- `selectedCorpusId` - 当前选择的语料库ID
- `hasSelectedCorpus` - 是否已选择过语料库
- `repeatCount` - 听读模式重复次数（1-5）

### 5. CorpusLoader - 语料库列表加载

**文件**: `app/src/main/java/com/learne/data/repository/CorpusLoader.kt`

- 从 `http://macrossfev.diskstation.me:44000/learne/corpora/list.json` 获取语料库列表
- 失败时返回空列表，调用方使用内置默认值

### 6. CorpusRepository - 词库数据源

**文件**: `app/src/main/java/com/learne/data/repository/CorpusRepository.kt`

- 从 `http://macrossfev.diskstation.me:44000/learne/corpora/{corpusId}/data.json` 加载词库
- HTTP 超时设置：connectTimeout=3s, readTimeout=5s
- `loadWords()` 为 suspend 函数，在 coroutine 中调用
- `getAudioPath()` 根据 word + type 生成音频 URL

### 7. AudioPlayer - 音频播放

**文件**: `app/src/main/java/com/learne/service/AudioPlayer.kt`

- 封装 MediaPlayer，支持异步 prepareAsync
- `play(url, onComplete)` 播放音频，完成后回调 duration
- 支持 `stop()` / `release()` 清理资源

## 数据流

```
HomeFragment → 选择语料库 + 模式
    ↓
InteractiveLearnFragment / ListenReadFragment
    ↓
CorpusRepository (network)
    ↓
动态构建 View / 自动播放音频
    ↓
ProgressRepository (写入DB)
WrongWordDao (错题记录)
```

## 关键配置

- **主题**: `Theme.MaterialComponents.Light.NoActionBar`
- **ViewBinding**: 已启用
- **Room Database**: 已启用（AppDatabase）
- **Gson**: JSON 解析
- **Kotlin Coroutines**: lifecycleScope + withTimeout

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
