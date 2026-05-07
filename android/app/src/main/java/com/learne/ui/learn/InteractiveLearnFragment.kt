package com.learne.ui.learn

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.learne.data.model.Word
import com.learne.data.repository.CorpusRepository
import com.learne.data.repository.ProgressRepository
import com.learne.data.repository.UserManager
import com.learne.data.repository.UserPreferencesRepository
import com.learne.databinding.FragmentInteractiveLearnBinding
import com.learne.service.AudioPlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale

class InteractiveLearnFragment : Fragment() {

    companion object {
        fun newInstance(corpusId: String = "catti"): InteractiveLearnFragment {
            val fragment = InteractiveLearnFragment()
            fragment.currentCorpusId = corpusId
            return fragment
        }
    }

    private var _binding: FragmentInteractiveLearnBinding? = null
    private val binding get() = _binding!!
    private lateinit var corpusRepo: CorpusRepository
    private lateinit var progressRepo: ProgressRepository
    private var words: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentStep = 1 // 1=display, 2=choice, 3=spell
    private var isInteractiveMode = true
    private var currentCorpusId: String = "catti"

    private val audioTypes = listOf("words", "meanings", "phrases", "phrase_meanings", "examples", "example_meanings")
    private var audioQueue: List<String> = emptyList()
    private var audioQueueIndex = 0
    private var isPlayingAudioSequence = false
    private val audioPlayer = AudioPlayer()

    // Choice state
    private var choiceOptions: List<ChoiceOption> = emptyList()
    private var selectedChoiceIndex = -1

    data class ChoiceOption(
        val meaning: String,
        val correct: Boolean
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInteractiveLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        corpusRepo = CorpusRepository(requireContext())
        progressRepo = ProgressRepository(requireContext())

        binding.btnStartLearn.setOnClickListener {
            startLearning()
        }
        binding.btnPrev.setOnClickListener { prevWord() }
        binding.btnNext.setOnClickListener { nextWord() }
        binding.btnUnfamiliar.setOnClickListener { markUnfamiliar() }
        binding.btnModeSwitch.setOnClickListener { toggleMode() }
    }

    private fun startLearning() {
        binding.btnStartLearn.text = "加载中..."
        binding.btnStartLearn.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withTimeout(15000) {
                    corpusRepo.loadWords(currentCorpusId)
                }
                words = result.take(20)
                if (words.isEmpty()) {
                    Toast.makeText(context, "没有可学习的单词", Toast.LENGTH_SHORT).show()
                    binding.btnStartLearn.text = "开始学习"
                    binding.btnStartLearn.isEnabled = true
                    return@launch
                }
                currentIndex = 0
                currentStep = 1
                showLearningUI()
                showCurrentWord()
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnStartLearn.text = "开始学习"
                binding.btnStartLearn.isEnabled = true
            }
        }
    }

    private fun showLearningUI() {
        binding.layoutWelcome.visibility = View.GONE
        binding.layoutTopBar.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.VISIBLE
        binding.layoutBottomButtons.visibility = View.VISIBLE
        updateProgressUI()
    }

    private fun updateProgressUI() {
        binding.tvProgress.text = "${currentIndex + 1} / ${words.size}"
        binding.tvStep.text = "步骤 $currentStep/3"
        val progress = ((currentIndex + 1) * 100) / words.size
        binding.progressBar.progress = progress
    }

    private fun showCurrentWord() {
        if (words.isEmpty()) return
        val word = words[currentIndex]
        binding.contentContainer.removeAllViews()

        when (currentStep) {
            1 -> showWordDisplay(word)
            2 -> showChoices(word)
            3 -> showSpellInput(word)
        }
        updateProgressUI()
    }

    // ====== Step 1: Word Display ======

    private fun showWordDisplay(word: Word) {
        val ctx = requireContext()
        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        // Word card
        val card = verticalLayout(dip(16), true)
        card.setBackgroundColor(0xFFFFFFFF.toInt())

        // Word + phonetic
        val wordText = textView(word.word, 28f, true, "#000000")
        wordText.setPadding(dp16, dp12, dp16, 0)
        card.addView(wordText)

        val phoneticText = textView(word.phonetic, 16f, false, "#808080")
        phoneticText.setPadding(dp16, 4, dp16, 0)
        card.addView(phoneticText)

        val marginLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        marginLayoutParams.setMargins(0, dp8, 0, 0)
        marginLayoutParams.topMargin = dp8
        card.addView(textView("", 8f, false, "#000000"), marginLayoutParams) // spacer

        // Meaning
        val meaningLabel = textView("释义", 14f, true, "#E3000F")
        meaningLabel.setPadding(dp16, 0, dp16, 4)
        card.addView(meaningLabel)

        val meaningText = textView(word.meaning, 16f, false, "#000000")
        meaningText.setPadding(dp16, 0, dp16, dp12)
        card.addView(meaningText)

        card.setPadding(dp12, dp12, dp12, dp12)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 0, 0, dp8)
        binding.contentContainer.addView(card, cardParams)

        // Phrase card
        if (word.phrase.isNotBlank()) {
            val phraseCard = verticalLayout(dip(16), true)
            phraseCard.setBackgroundColor(0xFFFFFFFF.toInt())

            val phraseLabel = textView("短语", 14f, true, "#0039CB")
            phraseLabel.setPadding(dp16, dp12, dp16, 4)
            phraseCard.addView(phraseLabel)

            val phraseText = textView(word.phrase, 16f, false, "#000000")
            phraseText.setPadding(dp16, 0, dp16, 0)
            phraseCard.addView(phraseText)

            if (word.phraseMeaning.isNotBlank()) {
                val phraseMeaningText = textView(word.phraseMeaning, 14f, false, "#808080")
                phraseMeaningText.setPadding(dp16, 4, dp16, dp12)
                phraseCard.addView(phraseMeaningText)
            }

            phraseCard.setPadding(dp12, 0, dp12, dp12)
            val phraseParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            phraseParams.setMargins(0, 0, 0, dp8)
            binding.contentContainer.addView(phraseCard, phraseParams)
        }

        // Example card
        if (word.example.isNotBlank()) {
            val exampleCard = verticalLayout(dip(16), true)
            exampleCard.setBackgroundColor(0xFFFFFFFF.toInt())

            val exampleLabel = textView("例句", 14f, true, "#0039CB")
            exampleLabel.setPadding(dp16, dp12, dp16, 4)
            exampleCard.addView(exampleLabel)

            val exampleText = textView(word.example, 15f, false, "#000000")
            exampleText.setPadding(dp16, 0, dp16, 0)
            exampleCard.addView(exampleText)

            if (word.exampleMeaning.isNotBlank()) {
                val exampleMeaningText = textView(word.exampleMeaning, 14f, false, "#808080")
                exampleMeaningText.setPadding(dp16, 4, dp16, dp12)
                exampleCard.addView(exampleMeaningText)
            }

            exampleCard.setPadding(dp12, 0, dp12, dp12)
            val exampleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            binding.contentContainer.addView(exampleCard, exampleParams)
        }

        // Next button for step
        val nextStepBtn = android.widget.Button(ctx).apply {
            text = "下一步"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setPadding(dp12, dp12, dp12, dp12)
            setOnClickListener { nextStep() }
        }
        val nextParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dip(48)
        )
        nextParams.topMargin = dp16
        binding.contentContainer.addView(nextStepBtn, nextParams)

        // Auto-play audio sequence
        playAudioSequence(word)
    }

    // ====== Step 2: Multiple Choice ======

    private fun showChoices(word: Word) {
        audioPlayer.release()
        isPlayingAudioSequence = false

        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        // Question prompt
        val prompt = textView("选择正确的释义", 20f, true, "#000000")
        prompt.setPadding(dp16, dp12, dp16, dp8)
        binding.contentContainer.addView(prompt)

        val wordHint = textView(word.word, 18f, true, "#E3000F")
        wordHint.setPadding(dp16, 0, dp16, dp16)
        binding.contentContainer.addView(wordHint)

        // Generate choices
        choiceOptions = generateChoices(word, words)
        selectedChoiceIndex = -1

        for ((index, option) in choiceOptions.withIndex()) {
            val choiceBtn = android.widget.Button(requireContext()).apply {
                text = option.meaning
                textSize = 15f
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(dp16, dp16, dp16, dp16)
                gravity = android.view.Gravity.CENTER or android.view.Gravity.START
                minLines = 2
                setOnClickListener { selectChoice(index) }
            }
            val choiceParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            choiceParams.setMargins(0, 0, 0, dp8)
            binding.contentContainer.addView(choiceBtn, choiceParams)
        }
    }

    private fun generateChoices(currentWord: Word, allWords: List<Word>): List<ChoiceOption> {
        val others = allWords.filter { it.word != currentWord.word }
        val shuffled = others.shuffled().take(3)
        val wrongOptions = shuffled.map { ChoiceOption(it.meaning, false) }
        val correctOption = ChoiceOption(currentWord.meaning, true)
        return (wrongOptions + correctOption).shuffled()
    }

    private fun selectChoice(index: Int) {
        if (selectedChoiceIndex >= 0) return // already selected
        selectedChoiceIndex = index
        val option = choiceOptions[index]
        val word = words[currentIndex]

        // Find the clicked button and color it
        val container = binding.contentContainer
        val choiceBtn = container.getChildAt(index + 2) as? android.widget.Button // skip prompt + wordHint
        if (choiceBtn != null) {
            if (option.correct) {
                choiceBtn.setBackgroundColor(0xFF4CAF50.toInt()) // green
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                Toast.makeText(context, "正确!", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(800)
                    currentStep = 3
                    resetInputState()
                    showCurrentWord()
                }
            } else {
                choiceBtn.setBackgroundColor(0xFFF44336.toInt()) // red
                choiceBtn.setTextColor(0xFFFFFFFF.toInt())
                Toast.makeText(context, "错误，再想想", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    choiceBtn.setBackgroundColor(0xFFFFFFFF.toInt())
                    choiceBtn.setTextColor(0xFF000000.toInt())
                    selectedChoiceIndex = -1
                }
                // Save wrong word
                saveError(word, "choice", option.meaning, word.meaning)
            }
        }
    }

    // ====== Step 3: Spelling ======

    private fun showSpellInput(word: Word) {
        audioPlayer.release()
        isPlayingAudioSequence = false

        val dp8 = dip(8)
        val dp12 = dip(12)
        val dp16 = dip(16)

        // Prompt
        val prompt = textView("根据释义写出单词", 20f, true, "#000000")
        prompt.setPadding(dp16, dp12, dp16, dp8)
        binding.contentContainer.addView(prompt)

        val meaningHint = textView(word.meaning, 18f, false, "#E3000F")
        meaningHint.setPadding(dp16, 0, dp16, dp16)
        binding.contentContainer.addView(meaningHint)

        // Input
        val editText = EditText(requireContext()).apply {
            hint = "输入单词..."
            textSize = 18f
            setPadding(dp16, dp12, dp16, dp12)
            setBackgroundColor(0xFFFFFFFF.toInt())
            setMaxLines(1)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    binding.btnNext.isEnabled = s?.isNotBlank() == true
                }
            })
        }
        val editParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        editParams.setMargins(0, 0, 0, dp16)
        binding.contentContainer.addView(editText, editParams)

        // Submit button
        val submitBtn = android.widget.Button(requireContext()).apply {
            text = "提交"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setPadding(dp12, dp12, dp12, dp12)
            setOnClickListener {
                val input = editText.text.toString().trim()
                if (input.isEmpty()) return@setOnClickListener
                val expected = word.word.lowercase()
                val actual = input.lowercase(Locale.getDefault())

                if (actual == expected) {
                    Toast.makeText(context, "正确!", Toast.LENGTH_SHORT).show()
                    markAsLearned(word)
                    nextWord()
                } else {
                    Toast.makeText(context, "错误，正确答案: ${word.word}", Toast.LENGTH_LONG).show()
                    editText.setText("")
                    saveError(word, "spell", input, word.word)
                }
            }
        }
        val submitParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dip(48)
        )
        binding.contentContainer.addView(submitBtn, submitParams)

        binding.btnNext.isEnabled = false
    }

    private fun resetInputState() {
        selectedChoiceIndex = -1
        binding.btnNext.isEnabled = true
    }

    // ====== Navigation ======

    private fun nextStep() {
        if (currentStep < 3) {
            currentStep++
            resetInputState()
            showCurrentWord()
        }
    }

    private fun nextWord() {
        audioPlayer.release()
        isPlayingAudioSequence = false
        if (currentIndex < words.size - 1) {
            currentIndex++
            currentStep = 1
            resetInputState()
            showCurrentWord()
        } else {
            showCompletion()
        }
    }

    private fun prevWord() {
        audioPlayer.release()
        isPlayingAudioSequence = false
        if (currentIndex > 0) {
            currentIndex--
            currentStep = 1
            resetInputState()
            showCurrentWord()
        }
    }

    private fun markUnfamiliar() {
        val word = words.getOrNull(currentIndex) ?: return
        lifecycleScope.launch {
            val db = com.learne.data.db.AppDatabase.getDatabase(requireContext())
            val uidCorpus = "${UserManager.userId}_$currentCorpusId"
            val existing = db.wrongWordDao().getByWord(uidCorpus, word.word)
            if (existing == null) {
                val timestamp = java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(java.util.Date())
                val wrongWord = com.learne.data.model.WrongWord(
                    id = "${UserManager.userId}_catti_${word.word}_$timestamp",
                    corpusId = uidCorpus,
                    word = word.word,
                    testType = "focus"
                )
                db.wrongWordDao().insert(wrongWord)
            }
        }
        Toast.makeText(context, "已标记为不熟", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMode() {
        isInteractiveMode = !isInteractiveMode
        binding.btnModeSwitch.text = if (isInteractiveMode) "听写模式" else "交互模式"
        Toast.makeText(context, if (isInteractiveMode) "切换到交互模式" else "切换到听写模式", Toast.LENGTH_SHORT).show()
    }

    private fun showCompletion() {
        binding.contentContainer.removeAllViews()
        binding.layoutBottomButtons.visibility = View.GONE

        val dp16 = dip(16)
        val title = textView("学习完成!", 28f, true, "#E3000F")
        title.gravity = android.view.Gravity.CENTER
        title.setPadding(dp16, dp16, dp16, dp16)
        binding.contentContainer.addView(title)

        val subtitle = textView("本次学习了 ${words.size} 个单词", 18f, false, "#808080")
        subtitle.gravity = android.view.Gravity.CENTER
        subtitle.setPadding(dp16, 0, dp16, dp16)
        binding.contentContainer.addView(subtitle)

        val restartBtn = android.widget.Button(requireContext()).apply {
            text = "重新学习"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFE3000F.toInt())
            setPadding(dp16, dp16, dp16, dp16)
            setOnClickListener {
                currentIndex = 0
                currentStep = 1
                binding.layoutBottomButtons.visibility = View.VISIBLE
                showCurrentWord()
            }
        }
        val restartParams = LinearLayout.LayoutParams(
            dip(200),
            dip(56)
        )
        restartParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
        restartParams.topMargin = dp16
        binding.contentContainer.addView(restartBtn, restartParams)
    }

    // ====== Audio ======

    private fun playAudioSequence(word: Word) {
        isPlayingAudioSequence = true
        audioQueue = audioTypes.map { corpusRepo.getAudioPath(currentCorpusId, word.word, it) }
        audioQueueIndex = 0
        playNextAudioInSequence()
    }

    private fun playNextAudioInSequence() {
        if (!isPlayingAudioSequence) return
        if (audioQueueIndex < audioQueue.size) {
            val path = audioQueue[audioQueueIndex]
            audioPlayer.play(path) { duration ->
                audioQueueIndex++
                playNextAudioInSequence()
            }
        } else {
            isPlayingAudioSequence = false
        }
    }

    private fun markAsLearned(word: Word) {
        lifecycleScope.launch {
            progressRepo.recordLearned(UserManager.userId, currentCorpusId, word.word)
        }
    }

    private fun saveError(word: Word, type: String, userAnswer: String, correctAnswer: String) {
        lifecycleScope.launch {
            val db = com.learne.data.db.AppDatabase.getDatabase(requireContext())
            val uidCorpus = "${UserManager.userId}_$currentCorpusId"
            val existing = db.wrongWordDao().getByWord(uidCorpus, word.word)
            if (existing != null) {
                db.wrongWordDao().update(
                    existing.copy(wrongCount = existing.wrongCount + 1, lastWrongTime = System.currentTimeMillis())
                )
            } else {
                val timestamp = java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(java.util.Date())
                val wrongWord = com.learne.data.model.WrongWord(
                    id = "${UserManager.userId}_catti_${word.word}_$timestamp",
                    corpusId = uidCorpus,
                    word = word.word,
                    testType = type
                )
                db.wrongWordDao().insert(wrongWord)
            }
        }
    }

    // ====== View Helpers ======

    private fun verticalLayout(padding: Int = 0, hasElevation: Boolean = false): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            if (padding > 0) setPadding(padding, padding, padding, padding)
            if (hasElevation) elevation = 4f
        }
    }

    private fun textView(text: String, size: Float, bold: Boolean, color: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = size
            this.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            this.setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun dip(dp: Int): Int {
        val scale = requireContext().resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun onDestroyView() {
        audioPlayer.release()
        super.onDestroyView()
        _binding = null
    }
}
