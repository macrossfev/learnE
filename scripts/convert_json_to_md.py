#!/usr/bin/env python3
"""将CET4 JSON数据转换为Markdown格式"""

import json

# 读取JSON数据
with open('/root/projects/LearnE/corpora/cet4/data.json', 'r', encoding='utf-8') as f:
    words = json.load(f)

# 生成Markdown
md_content = """# CET4 词汇表（词频 + 音标 + 词组 + 例句）

共 **{}** 个词条

| # | 单词 | 音标 | 词性 | 释义 | 常见词组 | 词组释义 | 例句 | 例句翻译 | 词频 |
|---|------|------|------|------|----------|----------|------|----------|------|
""".format(len(words))

for i, w in enumerate(words, 1):
    word = w.get('word', '')
    phonetic = w.get('phonetic', '')
    pos = w.get('pos', '') or w.get('part_of_speech', '')
    meaning = w.get('meaning', '')
    phrase = w.get('phrase', '')
    phrase_meaning = w.get('phrase_meaning', '')
    example = w.get('example', '')
    example_meaning = w.get('example_meaning', '')
    freq = w.get('freq_rank', 0) or w.get('freq', 0)

    # 格式化词性
    if pos and not pos.startswith('`'):
        pos = '`' + pos + '`'

    md_content += f"| {i} | {word} | {phonetic} | {pos} | {meaning} | {phrase} | {phrase_meaning} | {example} | {example_meaning} | {freq} |\n"

# 写入文件
with open('/root/projects/LearnE/corpora/cet4/data.md', 'w', encoding='utf-8') as f:
    f.write(md_content)

print(f"转换完成：{len(words)} 条词条")
print(f"输出文件：/root/projects/LearnE/corpora/cet4/data.md")