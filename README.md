# LearnE 英语单词学习项目

## 项目简介

LearnE 是一个英语单词学习应用，采用"最常见释义 + 最常见词组 + 最常见例句"的开发哲学，帮助用户高效记忆单词。

支持多语料库：CET4（大学英语四级）、CATTI（翻译专业资格考试），后续可扩展更多词汇库。

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
# 运行项目
```

## 环境要求

| 环境 | 版本 |
|-----|------|
| 微信开发者工具 | 最新稳定版 |
| Android Studio | 4.0+ |
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
├── android/              # Android版本
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
```

## 语料库说明

| 语料库 | 词条数 | 音频覆盖 |
|-------|-------|---------|
| CET4 | 3393 | 1000 (29%) |
| CATTI | 4807 | 4626 (100%) |

每个词条包含：单词、音标、词性、释义、词组、词组释义、例句、例句翻译、词频。

## 常用命令

```bash
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