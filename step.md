# LearnE 项目开发进度

## 更新时间: 2026-05-06 02:00

---

## ✅ 全部功能已完成并通过构建

### 已实现功能清单

**核心功能**：
- ✅ 自动播放模式（50词一组，6种音频顺序播放）
- ✅ 高达风格界面（红白蓝金属质感配色）
- ✅ HTTP远程语料库访问（域名+端口转发）
- ✅ HTTP远程音频播放

**数据层**：
- ✅ Room数据库（7张表）
- ✅ 错题本系统（自动记录+纠正）
- ✅ 学习记录统计（每日数据）
- ✅ 每日目标打卡（目标设置+连续天数）
- ✅ 用户笔记（单词笔记保存）
- ✅ 成就徽章系统（6个成就）
- ✅ 学习提醒设置（定时提醒）

**界面**：
- ✅ 学习界面（自动播放+进度条+控制面板）
- ✅ 统计界面（圆环进度+柱状图+成就列表）
- ✅ 搜索界面（词典查询+笔记编辑）
- ✅ 错题本界面（错题列表+标记纠正）
- ✅ 设置界面（语料库+学习模式+播放+显示+提醒）

**学习模式**：
- ✅ 自动播放模式（单词→词组→释义→例句→例句释义）
- ✅ 卡片翻转模式
- ✅ 词根词缀模式
- ✅ 听写模式

**个性化**：
- ✅ 播放速度调节（0.5x-2.5x）
- ✅ 夜间模式开关
- ✅ 显示音标/例句控制
- ✅ 学习提醒定时

---

## 开发经验总结

### 1. Android构建常见问题

**Gradle配置**：
```
问题: Gradle下载超时
解决: 使用国内镜像 mirrors.cloud.tencent.com/gradle

问题: JDK版本不兼容（Java 21 vs Gradle 8.1）
解决: 升级Gradle到8.5+ 或 使用Java 17

问题: 依赖下载失败
解决: settings.gradle配置阿里云镜像
```

**Room数据库**：
```
问题: Schema不匹配导致崩溃
解决: 升级version + fallbackToDestructiveMigration()

问题: AppDatabase_Impl不存在
解决: 添加kapt插件和room-compiler依赖

问题: exportSchema警告
解决: @Database(exportSchema = false)
```

**HTTP明文访问**：
```
问题: Android 9+禁止明文HTTP
解决: 添加network_security_config.xml，允许指定域名
```

**ViewBinding**：
```
问题: FragmentXxxBinding无法找到
解决: build.gradle添加 buildFeatures { viewBinding true }
```

### 2. 网络部署经验

**Caddy配置要点**：
- handle_path剥离路径前缀
- file_server browse提供文件浏览
- 端口转发：外网44000 → 内网80

**访问地址**：
```
http://macrossfev.diskstation.me:44000/learne/corpora/catti/data.json
http://macrossfev.diskstation.me:44000/learne/corpora/catti/audio/words/xxx.mp3
```

### 3. Kotlin编码注意事项

**Flow使用**：
```
需要导入: kotlinx.coroutines.flow.firstOrNull
类型推断: 某些情况需显式指定类型
    observe { records: List<StudyRecord> -> }
```

**数据类构造**：
```
@Entity必须有@PrimaryKey
所有参数必须提供值，不能省略
```

**XML命名空间**：
```
xmlns:app必须在根元素声明
CardView等控件才能使用app:属性
```

### 4. 音频播放

**MediaPlayer远程播放**：
```
setDataSource(url) - 直接用HTTP URL
prepareAsync() - 异步准备
setOnPreparedListener { start() } - 准备好后播放
```

---

## 文件统计

| 类型 | 数量 |
|-----|------|
| Kotlin文件 | 45+ |
| 布局文件 | 20+ |
| 数据表 | 7 |
| DAO接口 | 7 |

---

## 项目结构

```
LearnE/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/learne/
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/ (Word, WordProgress, StudyModels)
│   │   │   │   │   ├── db/ (AppDatabase, AllDaos)
│   │   │   │   │   └── repository/ (CorpusRepository, ProgressRepository, StudyRepository)
│   │   │   │   ├── service/ (AudioPlayer)
│   │   │   │   ├── di/ (ViewModelFactory)
│   │   │   │   └── ui/
│   │   │   │       ├── learn/ (AutoPlayViewModel, LearnFragment)
│   │   │   │       ├── stats/ (StatsViewModelNew, StatsFragmentNew, AchievementAdapter)
│   │   │   │       ├── search/ (SearchViewModel, SearchFragment, SearchResultsAdapter)
│   │   │   │       ├── wrong/ (WrongWordViewModel, WrongWordsFragment, WrongWordAdapter)
│   │   │   │       ├── settings/ (SettingsViewModelNew, SettingsFragmentNew)
│   │   │   │       └── main/ (MainActivity)
│   │   │   └── res/
│   │   │       ├── layout/ (20+布局)
│   │   │       ├── drawable/ (高达风格按钮/进度条)
│   │   │       └── values/ (colors, themes, strings)
│   │   └── build.gradle
│   ├── build.gradle
│   └── settings.gradle
├── corpora/
│   ├── cet4/data.json
│   └ catti/data.json
├── step.md
└── docs/
```

---

## 服务器部署

**位置**: `/var/www/learne/`
**配置**: `/etc/caddy/Caddyfile`
**音频大小**: CATTI 375MB

---

## 下一步优化建议

1. 音频预加载优化
2. 离线缓存支持
3. 真题例句拓展
4. 词根词缀数据补充
5. 发音评分（可选）
6. 微信小程序版本

---

## 关键代码片段

### 自动播放顺序
```kotlin
enum class PlayState {
    WORD, PHRASE, PHRASE_MEANING, EXAMPLE, EXAMPLE_MEANING
}
```

### 数据库表
```kotlin
@Entity tableName = "wrong_words" // 错题
@Entity tableName = "study_records" // 每日记录
@Entity tableName = "daily_goal" // 打卡
@Entity tableName = "user_notes" // 笔记
@Entity tableName = "achievements" // 成就
```

### 高达配色
```xml
<color name="gundam_red">#E3000F</color>
<color name="gundam_blue">#0039CB</color>
<color name="gundam_white">#F0F0F0</color>
<color name="background">#1A1A2E</color>
```

---

**新增数据模型**：
- `WrongWord` - 错题记录
- `StudyRecord` - 每日学习统计
- `DailyGoal` - 每日目标打卡
- `UserNote` - 用户笔记
- `Achievement` - 成就徽章
- `StudyReminder` - 学习提醒

**新增DAO**：
- `WrongWordDao` - 错题本操作
- `StudyRecordDao` - 学习记录
- `DailyGoalDao` - 目标打卡
- `UserNoteDao` - 笔记管理
- `AchievementDao` - 成就系统
- `ReminderDao` - 提醒设置

**新增Repository**：
- `StudyRepository` - 统一管理所有学习数据

**新增布局**：
- `fragment_stats_new.xml` - 新统计界面（圆环进度、打卡）
- `fragment_search.xml` - 搜索界面
- `fragment_wrong_words.xml` - 错题本界面
- `item_achievement.xml` - 成就列表项
- `item_wrong_word.xml` - 错题列表项
- `progress_circle.xml` - 圆环进度条

**新增ViewModel/Adapter**：
- `StatsViewModelNew` - 统计功能
- `SearchViewModel` - 搜索功能
- `WrongWordViewModel` - 错题管理
- `AchievementAdapter` - 成就列表
- `SearchResultsAdapter` - 搜索结果
- `WrongWordAdapter` - 错题列表

### ⏳ 待实现

**功能模块**：
1. 多种学习模式（词根词缀、卡片翻转、听写）
2. 艾宾浩斯复习算法优化
3. 测试功能增强（连线题、限时挑战）
4. 个性化设置（播放速度、夜间模式、提醒）

**界面整合**：
- 更新导航图添加新Fragment
- Fragment实现类编写
- ViewModelFactory更新

---

## 文件统计

| 类型 | 数量 |
|-----|------|
| Kotlin文件 | 35+ |
| 布局文件 | 15+ |
| 数据模型 | 8 |
| DAO接口 | 7 |

---

## 下一步继续

1. 完成StatsFragment、SearchFragment、WrongWordsFragment实现
2. 更新导航添加新页面
3. 实现多种学习模式
4. 增强测试功能

---

LearnE 是英语单词学习应用，支持多语料库（CET4/CATTI），采用"最常见释义 + 最常见词组 + 最常见例句"的开发哲学。

**项目地址**：
- 服务器: `/root/projects/LearnE/`
- Windows本地: `D:\Andriodproject\LearnE`

**界面风格**：高达风格（红白蓝金属质感配色）

---

## 二、最新更新 - 自动播放功能

### 功能说明

**自动播放模式**：
- 每组50个单词
- 自动播放顺序：单词发音 → 词组发音 → 词组释义 → 例句发音 → 例句释义
- 预加载功能：选好组后全部加载
- 支持暂停/继续、上一组/下一组切换

**界面设计**：
- 高达配色：红色(#E3000F)、蓝色(#0039CB)、白色(#F0F0F0)
- 金属质感卡片、渐变进度条
- 深色背景(#1A1A2E)
- 分区显示：PHRASE/EXAMPLE标识

---

## 三、语料库数据

### 已完成

| 语料库 | 词条数 | JSON | MD | 音频 |
|-------|-------|------|-----|------|
| CET4 | 3393 | ✅ | ✅ | 部分(1000个) |
| CATTI | 4807 | ✅ | ✅ | ✅(完整) |

**文件位置**：
- `/root/projects/LearnE/corpora/cet4/data.json`
- `/root/projects/LearnE/corpora/catti/data.json`

---

## 三、服务器部署

### 已完成

**部署地址**：`http://macrossfev.diskstation.me:44000/learne/`

**服务器配置**：
- Caddy Web Server，端口80
- 外网端口转发: 44000 → 内网80
- 部署目录: `/var/www/learne/`

**访问测试**：
```bash
# 语料库JSON
curl http://macrossfev.diskstation.me:44000/learne/corpora/catti/data.json

# 音频文件
curl http://macrossfev.diskstation.me:44000/learne/corpora/catti/audio/words/ability.mp3
```

**Caddy配置** (`/etc/caddy/Caddyfile`)：
```
http://macrossfev.diskstation.me {
    handle_path /learne/* {
        root * /var/www/learne
        file_server browse
    }
    handle {
        reverse_proxy localhost:3000
    }
}
```

**部署文件**：
```
/var/www/learne/
├── corpora/
│   ├── cet4/
│   │   ├── data.json (1.1MB)
│   │   └── audio/ (待补充)
│   ├── catti/
│   │   ├── data.json (1.5MB)
│   │   └── audio/ (375MB, 6种类型)
│   │       ├── words/
│   │       ├── meanings/
│   │       ├── phrases/
│   │       ├── phrase_meanings/
│   │       ├── examples/
│   │       └── example_meanings/
```

---

## 四、Android 开发

### 项目结构

```
/root/projects/LearnE/android/
├── app/
│   ├── build.gradle (已配置ViewBinding、Gson)
│   └── src/main/
│       ├── java/com/learne/
│       │   ├── data/
│       │   │   ├── model/ (Word.kt, Corpus.kt, WordProgress.kt)
│       │   │   ├── db/ (AppDatabase.kt, ProgressDao.kt)
│       │   │   └── repository/ (CorpusRepository.kt, ProgressRepository.kt)
│       │   ├── di/ (ViewModelFactory.kt)
│       │   ├── service/ (AudioPlayer.kt)
│       │   └── ui/
│       │       ├── main/ (MainActivity.kt)
│       │       ├── learn/ (LearnFragment, LearnViewModel)
│       │       ├── review/ (ReviewFragment, ReviewViewModel)
│       │       ├── test/ (TestFragment, TestViewModel)
│       │       ├── stats/ (StatsFragment, StatsViewModel)
│       │       └── settings/ (SettingsFragment, SettingsViewModel)
│       ├── res/
│       │   ├── layout/ (8个布局文件)
│       │   ├── drawable/ (5个导航图标)
│       │   ├── mipmap/ (launcher图标)
│       │   ├── navigation/ (nav_graph.xml)
│       │   ├── menu/ (bottom_nav_menu.xml)
│       │   └── values/ (strings, colors, themes)
│       └── AndroidManifest.xml (已添加INTERNET权限)
├── build.gradle (Gradle Plugin 8.2.0)
├── settings.gradle (阿里云镜像)
├── gradle.properties (android.useAndroidX=true)
└── gradle/wrapper/ (Gradle 8.5)
```

### 已实现功能

- ✅ MVVM架构
- ✅ Room数据库持久化
- ✅ HTTP远程加载语料库
- ✅ HTTP远程播放音频
- ✅ 底部导航（5个模块）
- ✅ 单词学习模块
- ✅ 复习模块（间隔重复算法）
- ✅ 测试模块（选择题/拼写）
- ✅ 统计模块
- ✅ 设置模块（语料库切换）

### 关键配置

**API地址** (`CorpusRepository.kt`):
```kotlin
object Config {
    const val BASE_URL = "http://macrossfev.diskstation.me:44000/learne"
}
```

**依赖库版本**:
- Kotlin: 1.9.0
- Gradle Plugin: 8.2.0
- Gradle: 8.5
- compileSdk/targetSdk: 34
- Navigation: 2.7.7
- Room: 2.6.1
- Lifecycle: 2.7.0
- Gson: 2.10.1

### 待解决问题

- ⏳ Gradle同步报错：网络问题已配置阿里云镜像
- ⏳ local.properties需在Windows本地创建：
  ```
  sdk.dir=C:/Users/macrossfev/AppData/Local/Android/Sdk
  ```

### Windows本地配置

**local.properties** (需手动创建):
```
sdk.dir=C:/Users/macrossfev/AppData/Local/Android/Sdk
```

**文件位置**: `D:\Andriodproject\LearnE\local.properties`

---

## 五、下一步工作

1. 在Windows本地完成Android项目构建
2. 测试APP功能
3. 完善UI细节（测试/复习界面）
4. 补充CET4音频（可选）
5. 开发微信小程序版本

---

## 六、文件统计

- Kotlin文件: 20个
- 代码行数: ~1000行
- 布局文件: 8个
- 语料库JSON: 2个 (共2.6MB)
- CATTI音频: 375MB

---

## 七、关键命令

```bash
# 测试服务器访问
curl http://macrossfev.diskstation.me:44000/learne/corpora/catti/data.json

# 重启Caddy
caddy reload --config /etc/caddy/Caddyfile

# 查看项目结构
find /root/projects/LearnE/android -type f -name "*.kt"

# Windows创建local.properties
echo "sdk.dir=C:/Users/macrossfev/AppData/Local/Android/Sdk" > D:\Andriodproject\LearnE\local.properties
```

---

*文档生成时间: 2026-05-05 12:00*