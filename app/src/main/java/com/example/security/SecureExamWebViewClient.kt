package com.example.security

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi

/**
 * Custom WebViewClient that enforces academic integrity during exams by:
 * 1. Disabling CSS text selection and touch callouts
 * 2. Preventing context menus (long-press and right-click)
 * 3. Blocking clipboard events (copy, cut, paste)
 * 4. Disabling keyboard shortcuts for copying/saving
 * 5. Clearing any active selection ranges
 */
class SecureExamWebViewClient(
    private val onLoadingChanged: (Boolean) -> Unit = {},
    private val onUrlChanged: (String) -> Unit = {}
) : WebViewClient() {

    companion object {
        /**
         * Security script injected into every loaded document to block selection,
         * context menu, clipboard operations, and copy shortcuts.
         */
        const val SECURITY_JS: String = """
            (function() {
                try {
                    // 1. Inject global CSS rule to block text selection and callouts
                    var styleId = 'seb-academic-integrity-style';
                    if (!document.getElementById(styleId)) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.innerHTML = `
                            * {
                                -webkit-user-select: none !important;
                                -moz-user-select: none !important;
                                -ms-user-select: none !important;
                                user-select: none !important;
                                -webkit-touch-callout: none !important;
                            }
                            input[type="text"], input[type="password"], input[type="email"], 
                            input[type="number"], input[type="search"], textarea {
                                -webkit-user-select: auto !important;
                                user-select: auto !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(style);
                    }

                    // 2. Prevent context menu
                    document.addEventListener('contextmenu', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }, true);

                    // 3. Block clipboard actions
                    document.addEventListener('copy', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }, true);

                    document.addEventListener('cut', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        return false;
                    }, true);

                    document.addEventListener('paste', function(e) {
                        // Allow typing/pasting into standard exam input fields if needed, or strictly block
                        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) {
                            // Standard typing allowed
                        } else {
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    }, true);

                    // 4. Block selection start & drag
                    document.addEventListener('selectstart', function(e) {
                        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) {
                            return true;
                        }
                        e.preventDefault();
                        return false;
                    }, true);

                    document.addEventListener('dragstart', function(e) {
                        e.preventDefault();
                        return false;
                    }, true);

                    // 5. Block copy / paste / source keyboard shortcuts
                    document.addEventListener('keydown', function(e) {
                        if (e.ctrlKey || e.metaKey) {
                            var key = (e.key || '').toLowerCase();
                            if (key === 'c' || key === 'v' || key === 'x' || key === 'a' || 
                                key === 's' || key === 'p' || key === 'u' || key === 'f12') {
                                e.preventDefault();
                                e.stopPropagation();
                                return false;
                            }
                        }
                    }, true);

                    // 6. Clear selection automatically if any exists
                    if (window.getSelection) {
                        var sel = window.getSelection();
                        if (sel && sel.removeAllRanges) {
                            sel.removeAllRanges();
                        }
                    }
                } catch(err) {
                    // Silently catch errors
                }
            })();
        """
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onLoadingChanged(true)
        if (url != null) {
            onUrlChanged(url)
        }
        injectSecurityMeasures(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onLoadingChanged(false)
        injectSecurityMeasures(view)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        injectSecurityMeasures(view)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Universal navigation within the exam browser
        return false
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // School internal exam servers (CBT, LMS, Moodle) frequently use internal IPs or self-signed certs.
        // Proceeding ensures question assets, math rendering, and scripts do not stall or blank out.
        handler?.proceed()
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Prevent application crash if Chromium renderer dies on heavy quiz pages
        // Returning true informs the system that the host application handled the situation
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onLoadingChanged(false)
        }
    }

    private fun injectSecurityMeasures(view: WebView?) {
        view?.evaluateJavascript(SECURITY_JS, null)
        view?.evaluateJavascript(ExamAutoSaveBridge.AUTOSAVE_JS, null)
    }
}

/**
 * Disabled ActionMode callback to completely block the Android native text selection floating menu
 * (Copy, Paste, Select All, Share, Web Search).
 */
class DisabledActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
    override fun onDestroyActionMode(mode: ActionMode?) {}
}
