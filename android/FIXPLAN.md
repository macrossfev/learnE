# LearnE 修复计划

> 生成日期: 2026-05-19
> 基于: 全面代码审查 + 用户使用逻辑梳理

---

## P0 — 核心功能缺陷

### 1. 间隔重复算法形同虚设
- **文件**: `ProgressRepository.kt`, `InteractiveLearnFragment.kt`
- **问题**: REVIEW模式答对不推进stage，答错才写progress(重置stage=0)
- **修复**: 答对时 `stage = min(4, stage+1)`, `nextReviewTime = now + intervals[stage]`
- **间隔**: [1h, 1d, 3d, 7d, 14d]

### 2. 听写/闪卡/每日挑战与计划系统脱节
- **文件**: `DictationFragment.kt`, `FlashcardFragment.kt`, `DailyChallengeFragment.kt`
- **问题**: 从首页进入，使用全局corpusId，不走计划系统
- **修复**: 所有模式读取当前计划的corpusId和groupSize

## P1 — 重要体验问题

### 3. 选择/拼写步骤缺少正确答案反馈
- **文件**: `InteractiveLearnFragment.kt`, `DictationFragment.kt`
- **问题**: 选错后直接下一步，不知道正确答案
- **修复**: 选错时高亮正确答案，2秒后继续

### 4. 挑战地图缺少自动滚动和进度摘要
- **文件**: `ChallengeMapFragment.kt`
- **问题**: 160+组需手动滚动；无"已完成X/Y组"文字
- **修复**: 加载后自动滚动到当前组；顶部加进度文字

### 5. 每日挑战无"每日"逻辑
- **文件**: `DailyChallengeFragment.kt`
- **问题**: 随机取词，同天多次进入题目不同
- **修复**: 基于日期种子生成固定题目；追踪每日完成状态

## P2 — 体验优化

### 6. 拼写接受合理变体 + 跳过按钮
- **文件**: `InteractiveLearnFragment.kt`
- **问题**: organize/organise被判错；不会拼只能乱输
- **修复**: 常见变体映射表 + "跳过/查看答案"按钮

### 7. 连续学习天数 + 每词自动保存
- **文件**: `StudyStatsFragment.kt`, `InteractiveLearnFragment.kt`
- **问题**: 无连续天数激励；退出时才保存进度
- **修复**: 统计页加连续天数；每完成一词即保存

### 8. UX细节改进
- **文件**: 多个Fragment
- **内容**:
  - 学习/考试中返回键确认弹窗
  - 只有黄色组(已学未考)才能考试
  - 考试题目随机打乱
  - 听读模式显示当前段落标签
  - 个人中心加退出登录

## P3 — 长期优化

### 9. 离线基础词库
- 内置CET4词库为assets，确保首次安装离线可用

### 10. 深色模式
- 添加dark theme资源

---

## 已完成修复 (代码审查阶段)

- [x] 考试通过线 100%→80%
- [x] Fragment参数 Bundle传参
- [x] Fragment tag 设置
- [x] @Volatile prefs
- [x] getWordsForReview corpusId格式
- [x] AudioPlayer自释放
- [x] HomeNavigation回调清理
- [x] milestone分隔符位置
- [x] Handler→lifecycleScope
- [x] 裸CoroutineScope→lifecycleScope
- [x] StudyRecord复合主键
