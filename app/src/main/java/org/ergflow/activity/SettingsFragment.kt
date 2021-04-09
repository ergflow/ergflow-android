package org.ergflow.activity

import android.content.SharedPreferences.Editor
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.takisoft.preferencex.PreferenceFragmentCompat

/**
 * Threshold settings fragment.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val button: Preference? = findPreference("resetToDefault")
        button?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Get this application SharedPreferences editor
            val preferencesEditor: Editor =
                PreferenceManager.getDefaultSharedPreferences(this.context).edit()
            // Clear all the saved preference values.
            preferencesEditor.clear()
            // Read the default values and set them as the current values.
            PreferenceManager.setDefaultValues(context, R.xml.root_preferences, true)
            // Commit all changes.
            preferencesEditor.apply()
            preferenceScreen = null
            addPreferencesFromResource(R.xml.root_preferences)
            true
        }
    }
}
