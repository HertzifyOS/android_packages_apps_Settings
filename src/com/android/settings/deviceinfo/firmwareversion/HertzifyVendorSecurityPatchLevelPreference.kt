/*
 * Copyright (C) 2025 HertzifyOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import android.text.format.DateFormat
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.utils.getLocale
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.preference.PreferenceBinding
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class HertzifyVendorSecurityPatchLevelPreference :
    PreferenceMetadata,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceBinding {

    private var currentPatch: String? = null

    override val key: String
        get() = "hertzify_vendor_security_patch"

    override val title: Int
        get() = R.string.hertzify_vendor_security_patch

    override fun isAvailable(context: Context) = context.getVendorPatch().isNotEmpty()

    override fun getSummary(context: Context) = context.getVendorPatch()

    private fun Context.getVendorPatch(): String =
        currentPatch ?: getFormattedPatch().also { currentPatch = it }

    private fun Context.getFormattedPatch(): String {
        val rawPatch = SystemProperties.get("ro.vendor.build.security_patch", "")
            .ifEmpty { SystemProperties.get("ro.hertzify.build.vendor_security_patch", "") }

        if (rawPatch.isEmpty()) {
            return ""
        }

        return try {
            val template = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val patchDate = template.parse(rawPatch)
            val format = DateFormat.getBestDateTimePattern(getLocale(), "dMMMMyyyy")
            DateFormat.format(format, patchDate).toString()
        } catch (e: ParseException) {
            // If parsing fails, return raw string
            rawPatch
        }
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}