#!/usr/bin/env python3
"""将CATTI Markdown数据转换为JSON格式"""

import json
import re

# 读取Markdown数据
with open('/root/projects/LearnE/corpora/catti/data.md', 'r', encoding='utf-8') as f:
    content = f.read()

# 解析数据
words = []
for line in content.split('\n'):
    if not line.startswith('| ') or line.startswith('|---') or line.startswith('| #'):
        continue
    parts = line.split('|')
    if len(parts) < 11:
        continue

    # 解析字段
    word = parts[2].strip()
    if not word or word == '单词':
        continue

    entry = {
        'word': word,
        'phonetic': parts[3].strip(),
        'pos': parts[4].strip(),
        'meaning': parts[5].strip(),
        'phrase': parts[6].strip(),
        'phrase_meaning': parts[7].strip(),
        'example': parts[8].strip(),
        'example_meaning': parts[9].strip(),
        'freq': int(parts[10].strip() or 0)
    }
    words.append(entry)

# 写入JSON文件
with open('/root/projects/LearnE/corpora/catti/data.json', 'w', encoding='utf-8') as f:
    json.dump(words, f, ensure_ascii=False, indent=2)

print(f'转换完成：{len(words)} 条词条')
print(f'输出文件：/root/projects/LearnE/corpora/catti/data.json')