# Changelog

All notable changes to Burp Ollama will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-02-01

### Added

- **Ask Ollama** – Context menu on selected text in Repeater, Proxy, Decoder
- **Ollama tab** – Dedicated AI panel in HTTP request/response editors with conversation history
- **Prompt templates** – Explain, Explain headers, Analyze JS, Find vulns
- **Scanner integration** – Analyze and Validate false positive on audit issues (Burp Professional)
- **Login sequence** – AI-assisted login flow generation; Session Handling action
- **Settings** – Ollama URL, model, timeout, context size, streaming, system prompts
- **Advanced prompts** – Customize Explain headers, Analyze, Validate false positive, Decipher, Generate login
- **Route via Burp** – Option to send Ollama requests through Burp HTTP API (default: off)
- **Clean unload** – Executor shutdown on extension unload (BApp Store compliance)
