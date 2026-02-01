# Changelog

All notable changes to Burp Ollama will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-02-01

### Added

- **Conversation branching** – Suite Chat supports multiple branches; "New branch" forks from current point to try different follow-ups; branch switcher to switch between threads; "New conversation" to clear all and start fresh
- **Token usage stats** – Ollama API token counts (prompt + eval) shown in Quick prompt, Suite Chat, Compare tab, and batch analysis responses when available
- **Collaborator OOB payload suggestions** – Intruder submenu "Suggest OOB payloads" generates Burp Collaborator payloads and asks AI to suggest injection payloads (SSRF, XXE, command injection, etc.) using them; requires Burp Professional with Collaborator enabled; customizable prompt in Settings
- **UI polish** – Consistent padding, spacing, and alignment across all Ollama UI components (Suite tab, Settings, dialogs, Repeater/Decoder tab); shared UiConstants for cohesive look
- **Section borders** – Titled borders (Your message, Response, Prompt, Model responses) on Chat, Compare, Tasks, Suggestions, Analyzed tabs; clear input/output areas; visible typing regions and results partitions
- **Ask Ollama** – Context menu on selected text in Repeater, Proxy, Decoder
- **Ollama tab** – Dedicated AI panel in HTTP request/response editors with conversation history
- **Prompt templates** – Explain, Explain headers, Analyze JS, Find vulns
- **Scanner integration** – Analyze and Validate false positive on audit issues (Burp Professional)
- **Login sequence** – AI-assisted login flow generation; Session Handling action
- **Settings** – Ollama URL, model, timeout, context size, streaming, system prompts
- **Advanced prompts** – Customize Explain headers, Analyze, Validate false positive, Decipher, Generate login
- **Route via Burp** – Option to send Ollama requests through Burp HTTP API (default: off)
- **Clean unload** – Executor shutdown on extension unload (BApp Store compliance)
- **Context lozenges** – Request/Response checkboxes in Ollama tab to choose what to include in context
- **Send to Repeater/Intruder** – Parse AI response for HTTP requests; send to Repeater or Intruder with one click
- **Unified Ollama Suite tab** – Central hub for AI (general queries); Chat + Tasks sub-tabs
- **Proxy History + Site Map** – "Ask Ollama" on selected items; "Analyze selected (N items)" for multi-select
- **Ollama Tasks tab** – Central task list for all AI interactions (Suite, Repeater, context menu)
- **Copy to clipboard** – Copy response to clipboard from Ollama tab, Suite tab, and response dialog (paste into Repeater notes)
- **Loading indicators** – Indeterminate progress bar and "Querying Ollama…" label in Repeater tab, Suite tab; "Loading…" in context-menu response dialog; Ask button disabled during requests
- **Settings panel** – "Saved" status label timer now correctly clears after 2 seconds
- **UX polish** – Ask button enabled/disabled by context; Copy shows "Copied!" feedback; Enter (Repeater follow-up) / Ctrl+Enter (Suite prompt) to submit; Refresh model button in Repeater and Suite tabs; Tasks tab empty state; tooltips
- **Intruder payload suggestions** – Context menu "Intruder" submenu with "Suggest payloads" and "Suggest attack type" (Sniper, Battering ram, Pitchfork, Cluster bomb); customizable prompts in Settings
- **Proactive suggestions** – HTTP handler detects login, auth, API patterns; Suggestions sub-tab in Ollama tab; "Use in Chat" to pre-fill prompt; enable/disable in Settings
- **Explore issue** – Context menu "Explore issue" on Scanner findings; AI suggests follow-up HTTP requests to validate/exploit; Send to Repeater/Intruder in response dialog; customizable prompt in Settings
- **Notes integration** – Include Notes checkbox in Repeater Ollama tab when item has notes; notes included in AI context when checked
- **Visual indicators** – "✓ Analyzed by Ollama" in context menu when item was previously analyzed (Proxy/Site map, message editor)
- **Per-tool model override** – Optional model overrides for Repeater tab and Suite tab in Settings (leave blank to use default)
- **Report snippet generation** – "Copy as report snippet" button in Repeater, Suite tab, and response dialog; formats for vulnerability reports
- **Autonomous Explore** – Context menu "Autonomous Explore" on Scanner findings; AI sends follow-up HTTP requests to validate/exploit (max 5 iterations, in-scope only); confirmation dialog before starting
- **Append to notes** – "Append to notes" button in Repeater Ollama tab; appends AI response to Repeater tab notes via Montoya Annotations API
- **Decoder model override** – Optional model override for Decoder in Settings; per-tool model selection (Repeater, Suite, Decoder)
- **Autonomous Explore settings** – Configurable max iterations (1–20), delay between requests (ms), and prompt in Settings
- **Send to Organizer** – "Send to Organizer" button in Repeater, Suite tab, and response dialog; sends extracted HTTP requests, fetches response, adds to Organizer
- **Recently analyzed list** – "Analyzed" tab in Ollama Suite tab showing items analyzed by Ollama (method + URL); auto-refreshes when new analyses complete
- **Ctrl+E hotkey** – Explain selection in HTTP message editor; also available in command palette
- **Use selection** – "Use selection" checkbox in Repeater Ollama tab when text is selected in content preview; uses selected text as focused context
- **Stop button** – Stop button in Autonomous Explore streaming dialog to interrupt long-running exploration
- **Executive summary** – AI-generated summary (findings, impact, next steps) appended when Autonomous Explore completes
- **Quick prompt dialog** – Compact non-modal dialog for one-off AI queries; accessible via Ollama menu or "Quick prompt" button in Suite tab
- **Markdown code block styling** – AI response areas (Suite Chat, Repeater tab, Quick prompt, response dialogs, batch/compare results) render ``` code blocks with monospace font and gray background instead of raw markdown
