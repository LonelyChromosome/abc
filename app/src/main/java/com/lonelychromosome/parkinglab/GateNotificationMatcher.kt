package com.lonelychromosome.parkinglab

import android.app.Notification
import android.os.Bundle
import java.text.Normalizer
import java.util.Locale

/** Conservative filter. Real Phenikaa Uni push samples are needed to tune wording. */
object GateNotificationMatcher {
    const val PHENIKAA_PACKAGE = "phx.smartschool.phenikaauniversity"

    private fun plain(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    fun isEntryText(value: String): Boolean {
        val text = plain(value)
        val entry = listOf("vao cong", "vao truong", "vao bai", "check in", "check-in", "gui xe", "xe da vao", "vehicle in", "action_check_in_paring")
            .any { text.contains(it) }
        val exit = listOf("ra cong", "ra truong", "ra bai", "check out", "check-out", "xe da ra", "action_check_out_paring")
            .any { text.contains(it) }
        return entry && !exit
    }

    fun isGateEntry(pkg: String, extras: Bundle?): Boolean {
        if (pkg != PHENIKAA_PACKAGE || extras == null) return false
        val fields = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT
        )
        val body = fields.joinToString(" ") { extras.getCharSequence(it)?.toString() ?: "" }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() } ?: ""
        return isEntryText(body + " " + lines)
    }
}
