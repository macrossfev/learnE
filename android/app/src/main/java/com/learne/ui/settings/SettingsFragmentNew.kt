package com.learne.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learne.R
import com.learne.databinding.FragmentSettingsNewBinding
import com.learne.data.repository.StudyRepository

class SettingsFragmentNew : Fragment() {

    private var _binding: FragmentSettingsNewBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModelNew

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModelNew(StudyRepository(requireContext())) as T
            }
        })[SettingsViewModelNew::class.java]

        viewModel.initPrefs(requireContext())

        setupSpinner()
        setupListeners()
        setupObservers()

        viewModel.load()
    }

    private fun setupSpinner() {
        val intervals = arrayOf("慢速 (3秒)", "正常 (2秒)", "快速 (1秒)", "极快 (0.5秒)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spInterval.adapter = adapter
    }

    private fun setupListeners() {
        binding.rgCorpus.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_cet4 -> viewModel.setCorpus("cet4")
                R.id.rb_catti -> viewModel.setCorpus("catti")
            }
        }

        binding.rgLearnMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_mode_auto -> viewModel.setLearnMode("auto")
                R.id.rb_mode_card -> viewModel.setLearnMode("card")
                R.id.rb_mode_root -> viewModel.setLearnMode("root")
                R.id.rb_mode_listen -> viewModel.setLearnMode("listen")
            }
        }

        binding.sbSpeed.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f + 0.5f
                binding.tvSpeedValue.text = "${speed}x"
                viewModel.setPlaySpeed(speed)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.swNightMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNightMode(isChecked)
        }

        binding.swShowPhonetic.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setShowPhonetic(isChecked)
        }

        binding.swShowExample.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setShowExample(isChecked)
        }

        binding.swReminder.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setReminderEnabled(isChecked)
        }

        binding.btnSetReminder.setOnClickListener {
            val hour = binding.etReminderHour.text.toString().toIntOrNull() ?: 9
            val minute = binding.etReminderMinute.text.toString().toIntOrNull() ?: 0
            viewModel.setReminderTime(hour, minute)
        }
    }

    private fun setupObservers() {
        viewModel.corpusId.observe(viewLifecycleOwner) { id ->
            binding.rbCet4.isChecked = id == "cet4"
            binding.rbCatti.isChecked = id == "catti"
        }

        viewModel.learnMode.observe(viewLifecycleOwner) { mode ->
            binding.rbModeAuto.isChecked = mode == "auto"
            binding.rbModeCard.isChecked = mode == "card"
            binding.rbModeRoot.isChecked = mode == "root"
            binding.rbModeListen.isChecked = mode == "listen"
        }

        viewModel.playSpeed.observe(viewLifecycleOwner) { speed ->
            binding.sbSpeed.progress = ((speed - 0.5f) * 100).toInt()
            binding.tvSpeedValue.text = "${speed}x"
        }

        viewModel.nightMode.observe(viewLifecycleOwner) { enabled ->
            binding.swNightMode.isChecked = enabled
        }

        viewModel.showPhonetic.observe(viewLifecycleOwner) { show ->
            binding.swShowPhonetic.isChecked = show
        }

        viewModel.showExample.observe(viewLifecycleOwner) { show ->
            binding.swShowExample.isChecked = show
        }

        viewModel.reminderEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.swReminder.isChecked = enabled
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}