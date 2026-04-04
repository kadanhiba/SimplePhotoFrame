package com.hiba.simplephotoframe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun attachBaseContext(newBase: Context) {
        val settings = SettingsManager(newBase).getSettings()
        super.attachBaseContext(LocaleHelper.setLocale(newBase, settings.language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager(this)
        val settings = settingsManager.getSettings()

        val etDuration = findViewById<EditText>(R.id.etDuration)
        val spinnerTransition = findViewById<Spinner>(R.id.spinnerTransition)
        val etTransitionDuration = findViewById<EditText>(R.id.etTransitionDuration)
        val cbShowClock = findViewById<CheckBox>(R.id.cbShowClock)
        val cbPreventBurnIn = findViewById<CheckBox>(R.id.cbPreventBurnIn)
        val cbAutoMuteVideo = findViewById<CheckBox>(R.id.cbAutoMuteVideo)
        val cbKeepScreenOn = findViewById<CheckBox>(R.id.cbKeepScreenOn)
        val cbUseSchedule = findViewById<CheckBox>(R.id.cbUseSchedule)
        val llSchedule = findViewById<LinearLayout>(R.id.llSchedule)
        val etStartTime = findViewById<EditText>(R.id.etStartTime)
        val etEndTime = findViewById<EditText>(R.id.etEndTime)
        val tvClockLocationLabel = findViewById<TextView>(R.id.tvClockLocationLabel)
        val spinnerClockLocation = findViewById<Spinner>(R.id.spinnerClockLocation)
        val spinnerDate = findViewById<Spinner>(R.id.spinnerDate)
        val rgOrder = findViewById<RadioGroup>(R.id.rgOrder)
        val cbDeveloperMode = findViewById<CheckBox>(R.id.cbDeveloperMode)
        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)

        // Setup Duration
        etDuration.setText(settings.durationSeconds.toString())

        // Setup Spinner Transitions
        val transitionsNames = arrayOf(
            getString(R.string.transition_none),
            getString(R.string.transition_fade),
            getString(R.string.transition_swap_right),
            getString(R.string.transition_swap_left),
            getString(R.string.transition_slide_up),
            getString(R.string.transition_slide_down),
            getString(R.string.transition_zoom),
            getString(R.string.transition_random)
        )
        val transitionsInternal = arrayOf("None", "Fade", "Swap Right", "Swap Left", "Slide Up", "Slide Down", "Zoom", "Random")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, transitionsNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTransition.adapter = adapter
        val transitionIndex = transitionsInternal.indexOf(settings.transition)
        if (transitionIndex >= 0) spinnerTransition.setSelection(transitionIndex)

        // Setup Transition Duration
        etTransitionDuration.setText(settings.transitionDurationSeconds.toString())

        // Setup Date Spinner
        val dateFormatsNames = arrayOf(getString(R.string.transition_none), "DD/MM", "DD/MM/YEAR")
        val dateFormatsInternal = arrayOf("None", "DD/MM", "DD/MM/YEAR")
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateFormatsNames)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDate.adapter = dateAdapter
        val dateIndex = dateFormatsInternal.indexOf(settings.dateDisplay)
        if (dateIndex >= 0) spinnerDate.setSelection(dateIndex)

        // Setup Clock
        cbShowClock.isChecked = settings.showClock

        // Setup Auto Mute
        cbAutoMuteVideo.isChecked = settings.autoMuteVideo

        // Setup Keep Screen On
        cbKeepScreenOn.isChecked = settings.keepScreenOn

        // Setup Schedule
        cbUseSchedule.isChecked = settings.useSchedule
        llSchedule.visibility = if (settings.useSchedule) View.VISIBLE else View.GONE
        etStartTime.setText(settings.startTime)
        etEndTime.setText(settings.endTime)

        // Setup Burn-In
        cbPreventBurnIn.isChecked = settings.preventBurnIn

        // Setup Clock Location Spinner
        val clockLocations = arrayOf("Top-Right", "Top-Left", "Bottom-Left", "Bottom-Right")
        val locationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, clockLocations)
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClockLocation.adapter = locationAdapter
        val locationIndex = clockLocations.indexOf(settings.clockLocation)
        if (locationIndex >= 0) spinnerClockLocation.setSelection(locationIndex)

        // Setup Language Spinner
        val languagesDisplay = arrayOf("System Default", "English", "עברית", "Français")
        val languagesCodes = arrayOf("System", "en", "iw", "fr")
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languagesDisplay)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = languageAdapter
        val languageIndex = languagesCodes.indexOf(settings.language)
        if (languageIndex >= 0) spinnerLanguage.setSelection(languageIndex)

        // Conditional visibility for Clock Location
        val updateVisibility = {
            val visible = !cbPreventBurnIn.isChecked
            tvClockLocationLabel.visibility = if (visible) View.VISIBLE else View.GONE
            spinnerClockLocation.visibility = if (visible) View.VISIBLE else View.GONE
        }
        updateVisibility()

        // Setup Order
        if (settings.order == "randomized") {
            findViewById<RadioButton>(R.id.rbRandom).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.rbSerial).isChecked = true
        }

        // Setup Developer Mode
        cbDeveloperMode.isChecked = settings.developerMode

        // AUTO-SAVE LOGIC
        val saveAction = { isLanguageChange: Boolean ->
            val rawDuration = etDuration.text.toString().toIntOrNull() ?: 5
            val duration = rawDuration.coerceIn(1, 3600) // Max 1 hour
            
            val transition = transitionsInternal[spinnerTransition.selectedItemPosition]
            
            val rawTransDuration = etTransitionDuration.text.toString().toFloatOrNull() ?: 0.5f
            val transitionDuration = rawTransDuration.coerceIn(0f, 10f) // Max 10 seconds

            val dateDisplay = dateFormatsInternal[spinnerDate.selectedItemPosition]
            val showClock = cbShowClock.isChecked
            val autoMuteVideo = cbAutoMuteVideo.isChecked
            val preventBurnIn = cbPreventBurnIn.isChecked
            val clockLocation = spinnerClockLocation.selectedItem.toString()
            val keepScreenOn = cbKeepScreenOn.isChecked
            val useSchedule = cbUseSchedule.isChecked
            val startTime = etStartTime.text.toString()
            val endTime = etEndTime.text.toString()
            val order = if (findViewById<RadioButton>(R.id.rbRandom).isChecked) "randomized" else "serial"
            val developerMode = cbDeveloperMode.isChecked
            val language = languagesCodes[spinnerLanguage.selectedItemPosition]

            val newSettings = SlideshowSettings(
                duration, transition, transitionDuration, showClock, dateDisplay,
                preventBurnIn, clockLocation, autoMuteVideo, keepScreenOn, useSchedule, startTime, endTime, order, developerMode, language
            )
            settingsManager.saveSettings(newSettings)

            if (isLanguageChange) {
                // Restart activity to apply language change
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

        // Listeners for auto-save
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { saveAction(false) }
        }

        etDuration.addTextChangedListener(textWatcher)
        etTransitionDuration.addTextChangedListener(textWatcher)
        etStartTime.addTextChangedListener(textWatcher)
        etEndTime.addTextChangedListener(textWatcher)

        val checkListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            llSchedule.visibility = if (cbUseSchedule.isChecked) View.VISIBLE else View.GONE
            updateVisibility()
            saveAction(false)
        }
        cbShowClock.setOnCheckedChangeListener(checkListener)
        cbPreventBurnIn.setOnCheckedChangeListener(checkListener)
        cbAutoMuteVideo.setOnCheckedChangeListener(checkListener)
        cbKeepScreenOn.setOnCheckedChangeListener(checkListener)
        cbUseSchedule.setOnCheckedChangeListener(checkListener)
        cbDeveloperMode.setOnCheckedChangeListener(checkListener)

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent?.id == R.id.spinnerLanguage) {
                    val currentLangCode = languagesCodes[position]
                    if (currentLangCode != settings.language) {
                        saveAction(true)
                    }
                } else {
                    saveAction(false)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerTransition.onItemSelectedListener = spinnerListener
        spinnerDate.onItemSelectedListener = spinnerListener
        spinnerClockLocation.onItemSelectedListener = spinnerListener
        spinnerLanguage.onItemSelectedListener = spinnerListener

        rgOrder.setOnCheckedChangeListener { _, _ -> saveAction(false) }

        findViewById<Button>(R.id.btnBackSettings).setOnClickListener {
            finish()
        }
    }
}
