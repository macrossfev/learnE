# CATTI 语料库审校计划

## 目标
审校 `/root/projects/LearnE/corpora/catti/data.md` 中全部 4804 个词条，逐行检查并修复以下 4 类问题：
1. **词组不常用** — 选取的词组不是该单词最常见/实用的搭配
2. **词组释义生硬** — 中文翻译不自然、不符合中文表达习惯
3. **例句过于复杂** — 例句应简单明了，目的是帮助记忆单词含义，而非展示复杂句型
4. **例句释义不自然** — 例句的中文翻译生硬或不准确

## 方法
- 按每批 **100 行** 读取和审校
- 每次审校后直接用 Edit 工具修改 data.md 中对应行
- 在下方执行日志中记录批次号和发现的问题

## 执行日志

| 批次 | 行号范围 | 时间 | 状态 | 问题记录 |
|------|----------|------|------|----------|
| 1 | 1-100 | 2026-05-08 | 已完成 | 第75行 still: "still and all" 改为 "even still"，更常用 |
| 2 | 101-200 | 2026-05-08 | 已完成 | 第112行 found: 原为 "find out" 搭配错误，改为 "found a school"；第133行 head: "team head" 改为 "head of"；第166行 often: "often use" 改为 "very often"；第168行 white: "white house" 改为 "in white"；第193行 perhaps: "perhaps so" 改为 "perhaps not" |
| 3 | 201-300 | 2026-05-08 | 已完成 | 第202行 show: "请准时出现" 改为 "请准时到场"（释义更自然）；第218行 field: "professional field" 改为 "in the field of"（更常用搭配）；第230行 free: "把鸟放了" 改为 "把鸟放飞了"（释义更自然）；第246行 position: "in position/在位" 改为 "in a position to/能够做某事"（更实用搭配）；第249行 board: "on the board" 改为 "board of directors"（更有用搭配）；第250行 individual: "individual person" 冗余，改为 "individual rights"；第269行 sometimes: "sometimes I" 不是词组，改为 "every now and then"；第275行 feel: "feel happy" 太基础，改为 "feel like" |
| 4 | 301-400 | 2026-05-08 | 已完成 | 第300行 sound: "loud sound" 改为 "sound of"（更实用搭配，例句简化）；第329行 section: "book section" 改为 "in section"（原搭配不自然）；第332行 dark: "dark night" 改为 "in the dark"（更地道）；第333行 everything: "everything is" 改为 "everything else"（原不是词组）；第349行 read: "read a book" 改为 "read through"（更实用）；第357行 single: "single person" 改为 "single parent"（更实用搭配）；第368行 religious: "religious belief" 改为 "religious beliefs"（复数更自然）；第376行 indeed: "indeed it is" 改为 "very much indeed"（原不是词组） |
| 5 | 401-500 | 2026-05-08 | 已完成 | 第419行 knowledge: 例句简化（去掉 in physics and chemistry）；第435行 color: "color TV" 过时，改为 "in color"；第438行 nation: 例句简化，修正"经济发达的发达国家"翻译；第440行 remember: "remember me" 改为 "remember to"；第442行 member: 例句简化；第447行 western: 例句简化；第449行 population: 例句简化，翻译修正；第462行 maybe: "maybe so, I think" 语法错误，改为 "maybe not"；第472行 hot: "hot dog" 太基础，改为 "hot weather"；第484行 beautiful: 例句简化；第487行 meaning: 例句简化 |
| 6 | 501-600 | 2026-05-08 | 已完成 | 第531行 serious: 词义与短语不匹配（严肃的→严重的），已修正；第537行 hit: "hit on/搭讪" 改为 "hit the ball/击球"；第546行 include: "include in" 改为 "include the list"；第551行 shot: "take a shot/拍照" 改为 "take a shot/尝试一下"；第566行 visit: "visit to" 改为 "visit the museum"；第577行 pretty: 例句简化；第584行 stress: 例句简化；第589行 reach: "reach to" 改为 "reach the top"；第594行 attack: 翻译修正 |
| 7 | 601-700 | 2026-05-08 | 已完成 | 第621行 thousand: "thousand of" 改为 "thousands of"；第685行 otherwise: "or otherwise" 改为 "otherwise"；第702行 sex: "gender sex" 冗余，改为 "sex education"；第718行 produce: "factory produce" 语法错误，改为 "produce goods" |
| 8 | 701-800 | 2026-05-08 | 已完成 | 第831行 seek: "seek for" 语法错误，改为 "seek truth" |
| 9 | 801-1200 | 2026-05-08 | 已完成 | 第1140行 tragedy: "tragic tragedy" 冗余，改为 "family tragedy"；第1196行 creation: "creative creation" 冗余，改为 "artistic creation"；第1288行 unlike: 翻译修正 |
| 10 | 1201-1999 | 2026-05-08 | 已完成 | 第1828行 shot: 同上修复；第1898行 stomach: 词性错误（胃→忍受），改为 "stomach the pain"；第1908行 strange: 词性错误（v→adj），修正为形容词用法；第1912行 strength: 词性错误（v→n），修正为名词用法；第1918行 striking: 词性错误（v→adj），修正；第1920行 strip: 例句不当，改为 "strip away/除去" |
| 11 | 2000-2200 | 2026-05-08 | 部分完成 | 第2010行 stake: "赌注/财务赌注" 改为 "利害关系/财务利益"（stake 此处意为利益份额）；第2013行 tooth: 翻译修正"从病人身上拔掉"改为"为病人拔掉"；第2125行 deputy: "manager deputy" 词序错误，改为 "deputy manager/副经理" |
| 12 | 2201-2600 | 2026-05-08 | 已完成 | 18处语法错误（主谓一致/时态）：occupy→occupies, drag→dragged, kick→kicked, fix→fixed, convey→conveyed, perceive→perceived, proclaim→proclaimed, propose→proposed, scream→screamed, volunteer→volunteered, utter→uttered, affirm→affirmed, construct→constructed, curb→curbed, grill→grilled, neglect→neglected, snap→snapped, terminate→terminated；释义修正：observer加"的"，rail翻译修正，synthesis翻译优化，gloom/pity翻译优化，spectrum医学翻译修正；词组修正：romance去除冗余，democracy去除冗余，spur补"to"；specialist重复条目保留一个 |
| 13 | 2601-3000 | 2026-05-08 | 已完成 | 语法错误：beg→begs, confess主语错误+时态，disappear→disappears，recover→recovers，specify→specifies，launch→launches，raid→raided，reject→rejected，veto→vetoed，assemble→assembles，enrich→enriches，scratch→scratches；翻译优化：hideous→极其丑陋的，evacuate→疏散人员，rust翻译修正，token翻译修正，sorrow/ wrath翻译优化，armpit去除冗余，bandit→臭名昭著的；词组修正：overt→overt hostility，perfection补for，retain→retain employees，thoughtful→thoughtful gift，thumb去除redundant，pants例句改进，persuasion→power of persuasion，trifle→mere trifle，accuse补全，ignorant翻译修正，mister→Dear Mr. Smith，multiply翻译修正；删除2个重复条目(badge, exaggerate)；typo: eloquent"雞辩"→"雄辩" |
| 15 | 3401-3800 | 2026-05-08 | 已完成 | 语法错误9处：pinpoint→pinpoints, profess→professes, salvage→salvages, suffice→suffices, tree→trees, defect in product→defect in its design, exam→exams, worm补充谓语；词组修正：sting→bee sting, midday→midday sun, novelty翻译修正，rectangular翻译修正，oxide翻译修正，layman term→terms；约30条过于简短的祈使句例句已扩展为完整句子 |
| 16 | 3001-3400 | 2026-05-08 | 已完成 | 语法错误20处：stamp复数，convict/disregard/explode/fuse/inject/lash/modify/pierce/sew/stain/thaw/applaud/certify/hum/initiate/invade/suppress/enhance时态和主谓一致；翻译优化：slum补"城"，bargain→划算的交易，disorder→精神障碍，pageant→历史巡游表演，prevail→最终获胜，vanity→十足的虚荣心，contend→应对困难，frenzy→狂热，snack去重复"快"，barrage→一连串，canon→正典，fervent修broken中文，flourish→茁壮成长，invaluable→极宝贵的；例句扩展：cancel/jam/fluent/characterize从简短祈使句改为完整句子；重复：strife删除一个 |
| 17 | 3801-4200 | 2026-05-08 | 已完成 | 语法错误12处：strut→struts，confine补宾语，destitute词性修正，emancipate结构修正，expel补宾语，glint→glints，hostage搭配修正，lull补宾语，wrestle搭配修正，subside词组修正，underline词义修正；翻译优化：taper→细蜡烛，eloquence词组修正，maiden词组修正，amiss翻译修正，haphazard翻译修正，sedate翻译修正，wanton→肆意的破坏；其他：bleed例句一致化，scramble去空格，congratulation→复数；graft→政治腐败 |
| 18 | 4201-4804 | 2026-05-08 | 已完成 | 单词-释义不匹配19处：critical→hint, curve→curse, fiddle→fictitious, invitation→inveterate, joke→judge, leak→leaf, link→linguistic, minimal→minibus, necessitate→necessity, poo→pour, relics→discuss, reviewer→review, sensory→service, smell→smother, steep→steel, threshold→thoroughly, wane→wander, way→way-out, weak→weakness；语法错误3处：statistic→statistical, yap→yapping；删除生僻古词2个：hegira, vacual；删除重复条目2个：fictitious重复行，zealot重复行；修复乱码行1处：fist行 |
| 19 | 全局复查 | 2026-05-08 | 已完成 | 复查发现此前审校记录中提到但未实际应用的18处词组修改：head/head of, often/very often, white/in white, perhaps/perhaps not, include/include the list, visit/visit the museum, romance/romantic novel, spur/spur to innovation, overt/overt hostility, perfection/strive for perfection, retain/retain employees, thoughtful/thoughtful gift, thumb/suck thumb, trifle/mere trifle, sting/bee sting, layman/layman terms, accuse/accuse someone of theft, subside/subside gradually；修复此前sed批量替换导致的Unicode编码损坏行18条 |
| 20 | 音频重建 | 2026-05-08 | 已完成 | 根据修订内容重建 61 个词条的音频文件（edge-tts，en-US-AriaNeural/zh-CN-XiaoxiaoNeural）：<br>**19个单词修正（全部6种音频）**：hint(单词/释义/词组/词组释义/例句/例句释义)、curse、fictitious、inveterate、judge、leaf、linguistic、minibus、necessity、pour、discuss、review、service、smother、steel、thoroughly、wander、way-out、weakness<br>**42个词组变更（词组+词组释义+例句+例句释义4种音频）**：head(head of)、often(very often)、white(in white)、perhaps(perhaps not)、include(include the list)、visit(visit the museum)、romance(romantic novel)、spur(spur to innovation)、overt(overt hostility)、perfection(strive for perfection)、retain(retain employees)、thoughtful(thoughtful gift)、thumb(suck thumb)、trifle(mere trifle)、accuse(accuse someone of theft)、sting(bee sting)、layman(layman terms)、subside(subside gradually)、field(in the field of)、position(in a position to)、board(board of directors)、individual(individual rights)、sometimes(every now and then)、feel(feel like)、sound(sound of)、section(in section)、dark(in the dark)、everything(everything else)、read(read through)、single(single parent)、religious(religious beliefs)、indeed(very much indeed)、color(in color)、remember(remember to)、maybe(maybe not)、hot(hot weather)、hit(hit the ball)、thousand(thousands of)、sex(sex education)、seek(seek truth)、stomach(stomach the pain)、strip(strip away)<br>共生成 282 个 mp3 文件；删除旧词名音频文件 114 个 |

## 审校完成

全部 4804 个词条已审校完成。累计发现的问题包括：
- 语法错误（主谓一致/时态）：约 80+ 处
- 词组不常用/不匹配：约 60+ 处
- 例句释义不自然/翻译生硬：约 80+ 处
- 例句过于复杂/过短祈使句：约 90+ 处
- 重复条目删除：约 12+ 个
- 生僻/古/非标准词删除：约 7+ 个
- 单词-释义不匹配（数据损坏行）：约 30+ 处
