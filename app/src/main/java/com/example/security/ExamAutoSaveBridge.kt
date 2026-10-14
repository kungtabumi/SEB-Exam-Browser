package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutoSaveState(
    val isAutoSaveEnabled: Boolean = true,
    val lastSavedTimestamp: Long? = null,
    val savedFieldsCount: Int = 0,
    val lastSavedUrl: String? = null,
    val isSavingAnimation: Boolean = false
)

class ExamAutoSaveBridge(
    private val context: Context,
    private val onStateUpdated: (AutoSaveState) -> Unit = {}
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("exam_form_autosave_prefs", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _autoSaveState = MutableStateFlow(AutoSaveState())
    val autoSaveState: StateFlow<AutoSaveState> = _autoSaveState.asStateFlow()

    @JavascriptInterface
    fun onFormAutoSaved(url: String, payloadJson: String, fieldsCount: Int) {
        val cleanKey = getStorageKeyForUrl(url)
        prefs.edit().putString(cleanKey, payloadJson).apply()

        mainHandler.post {
            val updated = _autoSaveState.value.copy(
                lastSavedTimestamp = System.currentTimeMillis(),
                savedFieldsCount = fieldsCount,
                lastSavedUrl = url,
                isSavingAnimation = true
            )
            _autoSaveState.value = updated
            onStateUpdated(updated)

            // Reset transient saving animation pulse after 1.5 seconds
            mainHandler.postDelayed({
                if (_autoSaveState.value.lastSavedTimestamp == updated.lastSavedTimestamp) {
                    _autoSaveState.value = _autoSaveState.value.copy(isSavingAnimation = false)
                }
            }, 1500)
        }
    }

    @JavascriptInterface
    fun getSavedFormData(url: String): String {
        val cleanKey = getStorageKeyForUrl(url)
        return prefs.getString(cleanKey, "") ?: ""
    }

    @JavascriptInterface
    fun onFormSubmitted(url: String) {
        val cleanKey = getStorageKeyForUrl(url)
        prefs.edit().remove(cleanKey).apply()

        mainHandler.post {
            val updated = _autoSaveState.value.copy(
                savedFieldsCount = 0
            )
            _autoSaveState.value = updated
            onStateUpdated(updated)
        }
    }

    fun clearSavedDataForUrl(url: String) {
        val cleanKey = getStorageKeyForUrl(url)
        prefs.edit().remove(cleanKey).apply()
        _autoSaveState.value = _autoSaveState.value.copy(savedFieldsCount = 0)
    }

    private fun getStorageKeyForUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path ?: ""
            // Include host + path and quiz attempt id if present, ignore transient session tokens
            val cmid = uri.getQueryParameter("cmid") ?: uri.getQueryParameter("id") ?: ""
            "draft_${uri.host}_${path}_${cmid}"
        } catch (_: Exception) {
            "draft_${url.hashCode()}"
        }
    }

    companion object {
        const val JAVASCRIPT_OBJ_NAME = "AndroidAutoSave"

        val AUTOSAVE_JS = """
            (function() {
                if (window.__examAutoSaveInitialized) return;
                window.__examAutoSaveInitialized = true;

                var AUTO_SAVE_INTERVAL_MS = 3000;
                var lastSavedHash = "";
                var saveTimeout = null;

                function getElementIdentifier(el) {
                    if (!el) return null;
                    if (el.id && el.id.trim().length > 0) return '#' + el.id.trim();
                    if (el.name && el.name.trim().length > 0) {
                        if (el.type === 'radio') {
                            return 'input[type="radio"][name="' + el.name + '"][value="' + el.value + '"]';
                        }
                        return el.tagName.toLowerCase() + '[name="' + el.name.trim() + '"]';
                    }
                    if (el.getAttribute('data-field-id')) {
                        return '[data-field-id="' + el.getAttribute('data-field-id') + '"]';
                    }
                    // Fallback to class/hierarchy in quiz questions (Moodle, CBT, Google Form, Canvas, LMS)
                    var questionContainer = el.closest('.que, .form-item, .fitem, .question, .soal, .card-soal, .cbt-question, .cbt-item, .exam-item, .item-soal, [role="listitem"]');
                    if (questionContainer && questionContainer.id) {
                        var tag = el.tagName.toLowerCase();
                        var idx = Array.prototype.indexOf.call(questionContainer.querySelectorAll(tag), el);
                        return '#' + questionContainer.id + ' ' + tag + ':nth-of-type(' + (idx + 1) + ')';
                    }
                    return null;
                }

                function collectFormResponses() {
                    var responses = {};
                    var count = 0;

                    // 1. Scan text inputs, numbers, textareas, and password
                    var textElements = document.querySelectorAll('input[type="text"], input[type="number"], input[type="email"], input[type="search"], input:not([type]), textarea');
                    textElements.forEach(function(el) {
                        if (el.disabled || el.readOnly) return;
                        var id = getElementIdentifier(el);
                        if (id && el.value !== undefined && el.value !== '') {
                            responses[id] = { type: 'text', value: el.value };
                            count++;
                        }
                    });

                    // 2. Scan Radio buttons
                    var radioElements = document.querySelectorAll('input[type="radio"]');
                    radioElements.forEach(function(el) {
                        if (el.checked && el.name) {
                            var id = 'radio_group_' + el.name;
                            responses[id] = { type: 'radio', name: el.name, value: el.value, selector: getElementIdentifier(el) };
                            count++;
                        }
                    });

                    // 3. Scan Checkboxes
                    var checkboxElements = document.querySelectorAll('input[type="checkbox"]');
                    checkboxElements.forEach(function(el) {
                        var id = getElementIdentifier(el);
                        if (id) {
                            responses[id] = { type: 'checkbox', checked: el.checked, value: el.value };
                            if (el.checked) count++;
                        }
                    });

                    // 4. Scan Select dropdowns
                    var selectElements = document.querySelectorAll('select');
                    selectElements.forEach(function(el) {
                        var id = getElementIdentifier(el);
                        if (id && el.selectedIndex >= 0) {
                            responses[id] = { type: 'select', value: el.value, selectedIndex: el.selectedIndex };
                            count++;
                        }
                    });

                    // 5. Scan Rich Text / WYSIWYG ContentEditable areas (Moodle Atto, TinyMCE, CKEditor)
                    var editableElements = document.querySelectorAll('[contenteditable="true"], .editor_atto_content, .tox-edit-area__iframe');
                    editableElements.forEach(function(el, index) {
                        var htmlContent = el.innerHTML || el.innerText || '';
                        if (htmlContent.trim().length > 0 && htmlContent !== '<p><br></p>') {
                            var id = el.id ? '#' + el.id : ('contenteditable_' + index);
                            responses[id] = { type: 'contenteditable', content: htmlContent };
                            count++;
                        }
                    });

                    return { data: responses, count: count };
                }

                function performAutoSave() {
                    try {
                        var collected = collectFormResponses();
                        if (collected.count === 0) return;

                        var jsonStr = JSON.stringify(collected.data);
                        if (jsonStr === lastSavedHash) return; // Unchanged
                        lastSavedHash = jsonStr;

                        // Save to local storage
                        var storageKey = 'exam_draft_' + window.location.pathname;
                        try {
                            localStorage.setItem(storageKey, jsonStr);
                        } catch(e) {}

                        // Send to Native Android Bridge
                        if (window.AndroidAutoSave && typeof window.AndroidAutoSave.onFormAutoSaved === 'function') {
                            window.AndroidAutoSave.onFormAutoSaved(window.location.href, jsonStr, collected.count);
                        }
                    } catch(err) {
                        console.error("AutoSave error: ", err);
                    }
                }

                function restoreSavedResponses() {
                    try {
                        var savedDataStr = "";
                        // Try native bridge first
                        if (window.AndroidAutoSave && typeof window.AndroidAutoSave.getSavedFormData === 'function') {
                            savedDataStr = window.AndroidAutoSave.getSavedFormData(window.location.href);
                        }
                        // Fallback to localStorage
                        if (!savedDataStr) {
                            var storageKey = 'exam_draft_' + window.location.pathname;
                            savedDataStr = localStorage.getItem(storageKey) || "";
                        }

                        if (!savedDataStr) return;
                        var savedData = JSON.parse(savedDataStr);
                        if (!savedData) return;

                        var restoredCount = 0;

                        Object.keys(savedData).forEach(function(key) {
                            var item = savedData[key];
                            if (!item) return;

                            if (item.type === 'text') {
                                var el = document.querySelector(key);
                                if (el && (!el.value || el.value === '')) {
                                    el.value = item.value;
                                    el.dispatchEvent(new Event('input', { bubbles: true }));
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                    restoredCount++;
                                }
                            } else if (item.type === 'radio') {
                                if (item.selector) {
                                    var el = document.querySelector(item.selector);
                                    if (el && !el.checked) {
                                        el.checked = true;
                                        el.dispatchEvent(new Event('change', { bubbles: true }));
                                        restoredCount++;
                                    }
                                } else if (item.name && item.value) {
                                    var el = document.querySelector('input[type="radio"][name="' + item.name + '"][value="' + item.value + '"]');
                                    if (el && !el.checked) {
                                        el.checked = true;
                                        el.dispatchEvent(new Event('change', { bubbles: true }));
                                        restoredCount++;
                                    }
                                }
                            } else if (item.type === 'checkbox') {
                                var el = document.querySelector(key);
                                if (el && el.checked !== item.checked) {
                                    el.checked = item.checked;
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                    restoredCount++;
                                }
                            } else if (item.type === 'select') {
                                var el = document.querySelector(key);
                                if (el) {
                                    el.value = item.value;
                                    if (item.selectedIndex !== undefined) el.selectedIndex = item.selectedIndex;
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                    restoredCount++;
                                }
                            } else if (item.type === 'contenteditable') {
                                var el = document.querySelector(key.startsWith('#') ? key : ('[contenteditable="true"]'));
                                if (el && (!el.innerText || el.innerText.trim() === '')) {
                                    el.innerHTML = item.content;
                                    el.dispatchEvent(new Event('input', { bubbles: true }));
                                    restoredCount++;
                                }
                            }
                        });

                        if (restoredCount > 0) {
                            console.log("[AutoSave] Restored " + restoredCount + " input responses.");
                        }
                    } catch(err) {
                        console.error("[AutoSave] Restore error: ", err);
                    }
                }

                // Debounced listener on user interaction
                function scheduleDebouncedSave() {
                    if (saveTimeout) clearTimeout(saveTimeout);
                    saveTimeout = setTimeout(performAutoSave, 1500);
                }

                document.addEventListener('input', scheduleDebouncedSave, true);
                document.addEventListener('change', scheduleDebouncedSave, true);
                document.addEventListener('keyup', scheduleDebouncedSave, true);

                // Periodic Interval Save
                setInterval(performAutoSave, AUTO_SAVE_INTERVAL_MS);

                // Form Submit Cleaner
                document.addEventListener('submit', function(e) {
                    try {
                        if (window.AndroidAutoSave && typeof window.AndroidAutoSave.onFormSubmitted === 'function') {
                            window.AndroidAutoSave.onFormSubmitted(window.location.href);
                        }
                        var storageKey = 'exam_draft_' + window.location.pathname;
                        localStorage.removeItem(storageKey);
                    } catch(err) {}
                }, true);

                // Attempt immediate restore and retry after dynamic scripts render (e.g. Moodle quiz questions)
                setTimeout(restoreSavedResponses, 500);
                setTimeout(restoreSavedResponses, 1500);
                setTimeout(restoreSavedResponses, 3000);

                window.__triggerManualAutoSave = performAutoSave;
            })();
        """.trimIndent()
    }
}

fun formatAutoSaveTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "Aktif"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return "Tersimpan " + sdf.format(Date(timestamp))
}
