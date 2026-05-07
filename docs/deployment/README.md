# 部署与运维文档

## 环境说明

| 环境 | 说明 |
|-----|------|
| dev | 本地开发环境 |
| test | 测试环境 |
| prod | 生产环境 |

## 微信小程序部署

### 1. 云开发配置
```json
// project.config.json
{
  "cloudfunctionRoot": "cloudfunctions/",
  "cloudbaseRoot": "cloudbase/"
}
```

### 2. 云函数部署
- 微信开发者工具 → 云函数目录 → 上传并部署

### 3. 云存储配置
- 创建音频文件目录
- 上传语料库音频文件

### 4. 数据库初始化
- 创建用户表
- 创建进度表
- 创建复习记录表

## Android版本部署

### 构建步骤
```bash
# Debug版本
./gradlew assembleDebug

# Release版本
./gradlew assembleRelease
```

### 发布流程
1. 签名打包
2. 上传应用商店/分发平台

## 配置文件说明

| 配置文件 | 说明 |
|---------|------|
| project.config.json | 小程序配置 |
| app.json | 小程序全局配置 |
| config.js | 应用参数配置 |

---

*创建时间: 2026-05-05*