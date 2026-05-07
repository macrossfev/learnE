# Android 开发进度

## 当前状态：v1.2.1 交互学习模式完成

### 已完成模块

#### 数据层
- ✅ `Word.kt` - 单词数据模型（Gson @SerializedName 支持下划线字段）
- ✅ `WrongWord.kt` - 错题记录实体
- ✅ `Corpus.kt` - 语料库模型（CET4/CATTI）
- ✅ `WordProgress.kt` - 学习进度实体
- ✅ `AppDatabase.kt` - Room数据库
- ✅ `ProgressDao.kt` - 进度数据访问对象
- ✅ `CorpusRepository.kt` - 语料库加载 + 音频路径生成
- ✅ `ProgressRepository.kt` - 进度管理

#### 服务层
- ✅ `AudioPlayer.kt` - 音频播放服务（异步 + 完成回调返回时长）
- ✅ `ViewModelFactory.kt` - ViewModel工厂

#### UI层 - 单词学习 (LearnFragment)
- ✅ 自动播放功能：播放顺序"单词→单词中文→词组→词组中文→例句→例句中文"
- ✅ 组号选择器：50词一组，Spinner可选择第几组播放
- ✅ 播放时屏幕常亮（keepScreenOn）
- ✅ 复读空隙：音频播放完成后等待与音频相当的时间（1-5秒）供用户自我复读
- ✅ 重复次数选择：×1/×2/×3 按钮，高亮当前选中
- ✅ 全屏卡片模式：根据当前播放音频展示单词、词组、例句及释义，底部 AUTOPLAY MODE 提示
- ✅ 组导航：上一组/下一组按钮

#### UI层 - 交互学习 (InteractiveLearnFragment)
- ✅ 3步交互学习流程：
  - Step 1: 单词展示（自动播放6段音频序列）
  - Step 2: 选择题（4选1，选对自动进Step3，选错标红1.5s后重置）
  - Step 3: 填词（显示中文释义，输入英文单词，填对写DB+下一词，填错显示答案2s后重填）
- ✅ 单词筛选：自动跳过已学/已掌握的单词，每次加载20个新词
- ✅ 手势导航：右滑进入下一步，左滑返回上一步
- ✅ "不熟"按钮：标记难词加入错题本
- ✅ 完成弹窗：学完一组弹出对话框显示统计
- ✅ 音频序列自动播放：6段音频连续播放
- ✅ 底部按钮上下文变化："认识→" / "→ 填词" / "确认"

#### UI层 - 复习 (ReviewFragment)
- ✅ 3步复习流程重写：
  - Step 1: 单词展示（自动播放音频）
  - Step 2: 选择题（4选1，随机干扰项）
  - Step 3: 填词题（显示中文释义，用户输入英文单词）
- ✅ 间隔重复算法：答对推进 1→3→7→15 天复习间隔
- ✅ 错题自动记录到 WrongWord 表

#### UI层 - 其他
- ✅ `TestFragment/ViewModel` - 测试模块
- ✅ `StatsFragment/ViewModel` - 统计模块
- ✅ `SettingsFragment/ViewModel` - 设置模块

#### 资源文件
- ✅ 5个底部导航图标（ic_learn, ic_review, ic_test, ic_stats, ic_settings）
- ✅ 6个布局文件（activity_main + 5个fragment）
- ✅ 2个测试/复习内容布局
- ✅ Navigation导航图
- ✅ 底部导航菜单
- ✅ 主题/颜色/字符串资源

#### 语料库数据
- ✅ CET4 data.json（3393词条）
- ✅ CATTI data.json（4807词条）

#### 新增文件列表
- ✅ `InteractiveLearnViewModel.kt` — 交互学习 ViewModel (3步流程+音频序列)
- ✅ `InteractiveLearnFragment.kt` — 交互学习 Fragment (手势+弹窗+UI绑定)
- ✅ `fragment_interactive_learn.xml` — 交互学习布局 (顶部进度/3步区域/底部按钮)

### 构建系统变更
- ✅ kapt → KSP 迁移（解决 JDK 17+ 模块访问问题）
  - `build.gradle`: `com.google.devtools.ksp` 插件
  - `app/build.gradle`: `ksp 'androidx.room:room-compiler:2.6.1'`
  - `gradle.properties`: `ksp.useWorkerMode=false`

### 待完成

- [ ] 音频文件部署（约500MB，建议云端存储或按需下载）
- [ ] 选择题测试UI完善
- [ ] 拼写测试UI完善
- [ ] 数据持久化测试
- [ ] 单元测试
- [ ] UI测试

## 构建说明

```bash
cd /root/projects/LearnE/android
# Android Studio: 打开项目，同步Gradle，运行
# 命令行构建（需要安装Android SDK）:
# ./gradlew assembleDebug
```

## 项目统计

- Kotlin文件：20个
- 布局文件：8个
- 资源文件：完整

---
*更新时间: 2026-05-06*