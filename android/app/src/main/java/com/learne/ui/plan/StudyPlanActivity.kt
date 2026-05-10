package com.learne.ui.plan

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.learne.data.model.Corpus
import com.learne.data.repository.CorpusLoader
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.ActivityStudyPlanBinding
import com.learne.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class StudyPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudyPlanBinding
    private var corpusList: List<Corpus> = emptyList()
    private var showingPlanList = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserPreferencesRepository.init(applicationContext)
        binding = ActivityStudyPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadCorpusList()
        updateContinueButton()

        binding.btnNewJourney.setOnClickListener {
            showNewPlanDialog()
        }

        binding.btnContinueJourney.setOnClickListener {
            showPlanList()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "选项（未实现）", Toast.LENGTH_SHORT).show()
        }

        binding.btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    private fun loadCorpusList() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = CorpusLoader.loadCorpusList()
                withContext(Dispatchers.Main) {
                    corpusList = if (result.isNotEmpty()) result else listOf(Corpus.CET4, Corpus.CATTI)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    corpusList = listOf(Corpus.CET4, Corpus.CATTI)
                }
            }
        }
    }

    private fun updateContinueButton() {
        if (UserPreferencesRepository.hasActivePlan) {
            binding.btnContinueJourney.isEnabled = true
            binding.btnContinueJourney.alpha = 1.0f
        } else {
            binding.btnContinueJourney.isEnabled = false
            binding.btnContinueJourney.alpha = 0.5f
        }
    }

    private fun showPlanList() {
        val plans = UserPreferencesRepository.getAllPlanSaves()

        if (plans.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没有存档")
                .setMessage("暂无可用的存档，请先创建新的学习。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val available = if (corpusList.isNotEmpty()) corpusList else listOf(Corpus.CET4, Corpus.CATTI)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(16), dip(8), dip(16), dip(8))
        }

        for ((i, plan) in plans.withIndex()) {
            val corpusName = available.find { it.id == plan.corpusId }?.name ?: plan.corpusId

            val completedCount = if (plan.totalGroups > 0) {
                val completed = UserPreferencesRepository.getPlanCompletedGroups(i)
                completed.size
            } else {
                val completed = UserPreferencesRepository.getCompletedGroups(plan.corpusId)
                completed.size
            }
            val total = if (plan.totalGroups > 0) plan.totalGroups else 0
            val pct = if (total > 0) completedCount * 100 / total else 0
            val progressText = if (total > 0) "$completedCount/$total 组完成 ($pct%)" else "未开始"

            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(plan.createdAt)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                elevation = 4f
                setPadding(dip(16), dip(12), dip(16), dip(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dip(8) }
            }

            // Top row: name + action buttons
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            topRow.addView(android.widget.TextView(this).apply {
                text = plan.name
                textSize = 17f
                setTextColor(0xFF333333.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { weight = 1f }
            })

            val planIndex = i

            // Action buttons
            listOf("重命名", "读取", "删除").forEachIndexed { idx, label ->
                val color = when (label) {
                    "重命名" -> 0xFF0039CB.toInt()
                    "删除" -> 0xFFE3000F.toInt()
                    else -> 0xFF000000.toInt()
                }
                topRow.addView(android.widget.Button(this).apply {
                    text = label
                    textSize = 13f
                    setTextColor(color)
                    setPadding(dip(12), dip(4), dip(12), dip(4))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dip(6) }
                    setOnClickListener {
                        when (label) {
                            "重命名" -> showRenameDialog(planIndex, plan.name) { showPlanList() }
                            "读取" -> loadPlan(plan)
                            "删除" -> AlertDialog.Builder(this@StudyPlanActivity)
                                .setTitle("删除存档")
                                .setMessage("确定删除「${plan.name}」吗？此操作不可撤销。")
                                .setPositiveButton("删除") { _, _ ->
                                    UserPreferencesRepository.deletePlan(planIndex)
                                    Toast.makeText(this@StudyPlanActivity, "已删除", Toast.LENGTH_SHORT).show()
                                    updateContinueButton()
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                })
            }

            card.addView(topRow)

            // Info row
            card.addView(android.widget.TextView(this).apply {
                text = "$corpusName · 每组 ${plan.groupSize} 词 · $progressText"
                textSize = 14f
                setTextColor(0xFF666666.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dip(6) }
            })

            // Date row
            card.addView(android.widget.TextView(this).apply {
                text = date
                textSize = 12f
                setTextColor(0xFF999999.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dip(2) }
            })

            container.addView(card)
        }

        AlertDialog.Builder(this)
            .setTitle("选择存档")
            .setView(container)
            .setCancelable(true)
            .setPositiveButton("返回", null)
            .create()
            .apply {
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.9).toInt(),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
            .show()
    }

    private fun loadPlan(plan: UserPreferencesRepository.PlanSave) {
        UserPreferencesRepository.planCorpusId = plan.corpusId
        UserPreferencesRepository.planGroupSize = plan.groupSize
        UserPreferencesRepository.planCurrentGroupIndex = plan.currentGroupIndex
        UserPreferencesRepository.planCurrentWordIndex = plan.currentWordIndex
        val planIndex = UserPreferencesRepository.getPlanIndex(plan.name)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("planIndex", planIndex.coerceAtLeast(0))
        }
        startActivity(intent)
        finish()
    }

    private fun showNewPlanDialog() {
        val available = if (corpusList.isNotEmpty()) corpusList else listOf(Corpus.CET4, Corpus.CATTI)

        val nameInput = EditText(this).apply {
            hint = "存档名称"
            setPadding(dip(16), dip(12), dip(16), dip(12))
        }

        // Group size preset options
        val groupSizeOptions = listOf(10, 20, 30, 40, 50)
        val selectedGroupSizeIndex = intArrayOf(groupSizeOptions.indexOf(UserPreferencesRepository.planGroupSize).coerceAtLeast(2))

        val groupSizeRadioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dip(16), dip(8), dip(16), dip(4))
            for ((i, size) in groupSizeOptions.withIndex()) {
                val label = if (size == 30) "$size（推荐）" else "$size"
                addView(android.widget.RadioButton(this@StudyPlanActivity).apply {
                    id = View.generateViewId()
                    text = label
                    textSize = 13f
                    isChecked = (i == selectedGroupSizeIndex[0])
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedGroupSizeIndex[0] = i
                    }
                })
            }
        }

        val selectedCorpusIndex = intArrayOf(available.indexOfFirst { it.id == "catti" }.coerceAtLeast(0))
        val corpusRadioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(24), dip(8), dip(24), dip(8))
            for ((i, corpus) in available.withIndex()) {
                addView(android.widget.RadioButton(this@StudyPlanActivity).apply {
                    id = View.generateViewId()
                    text = "${corpus.name} - ${corpus.description}"
                    textSize = 14f
                    isChecked = (i == selectedCorpusIndex[0])
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedCorpusIndex[0] = i
                    }
                })
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(android.widget.TextView(this@StudyPlanActivity).apply {
                text = "每组单词数"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dip(24), dip(8), dip(16), 0)
            })
            addView(groupSizeRadioGroup)
            addView(android.widget.TextView(this@StudyPlanActivity).apply {
                text = "词库"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dip(24), dip(4), dip(16), 0)
            })
            addView(corpusRadioGroup)
        }

        AlertDialog.Builder(this)
            .setTitle("创建新的学习")
            .setView(container)
            .setCancelable(true)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this@StudyPlanActivity, "请输入存档名称", Toast.LENGTH_SHORT).show()
                    showNewPlanDialog()
                    return@setPositiveButton
                }
                val groupSize = groupSizeOptions[selectedGroupSizeIndex[0]]
                val selected = available[selectedCorpusIndex[0]]
                UserPreferencesRepository.createPlan(name, selected.id, groupSize)
                UserPreferencesRepository.savePlan(selected.id, groupSize)
                val planIndex = UserPreferencesRepository.getPlanIndex(name)
                Toast.makeText(this@StudyPlanActivity, "已创建：$name", Toast.LENGTH_SHORT).show()
                updateContinueButton()

                // Directly enter
                val intent = Intent(this@StudyPlanActivity, MainActivity::class.java).apply {
                    putExtra("planIndex", planIndex.coerceAtLeast(0))
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(index: Int, currentName: String, onDone: () -> Unit) {
        val editText = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            setPadding(dip(16), dip(12), dip(16), dip(12))
        }
        AlertDialog.Builder(this)
            .setTitle("重命名学习")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    UserPreferencesRepository.renamePlan(index, newName)
                    onDone()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dip(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}
