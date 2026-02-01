# Burp Ollama

A Burp Suite extension that provides AI-powered security testing using **local Ollama models** instead of PortSwigger's paid Burp AI service.

## Features

- **Ask Ollama** – Right-click on selected text in Repeater, Proxy, or Decoder → "Ask Ollama" to get security-focused explanations
- **Privacy** – All data stays on your machine; nothing is sent to external APIs
- **Offline** – Works without internet (after models are pulled)
- **Configurable** – Ollama URL, model selection, timeout, context size, and system prompts

## Requirements

- Burp Suite (Community or Professional)
- [Ollama](https://ollama.com) installed and running locally
- At least one model pulled (e.g. `ollama pull llama3.2:3b`)

## Installation

1. Build the JAR: `./gradlew jar`
2. In Burp: **Extensions > Installed > Add** → Select `build/libs/burp-ollama.jar`
3. Configure in **Settings > Burp Ollama** (Ollama URL, model, etc.)

## Usage

1. Ensure Ollama is running (`ollama serve` or the Ollama app)
2. In Repeater, Proxy, or Decoder, select some text (e.g. an HTTP header, cookie, or JavaScript snippet)
3. Right-click → **Ask Ollama**
4. View the AI response in the dialog

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Ollama base URL | `http://localhost:11434` | Where Ollama API is running |
| Model | `llama3.2:3b` | Model to use (must be pulled in Ollama) |
| Timeout | 120s | Request timeout |
| Context size | 4096 | `num_ctx` for Ollama |
| Streaming | Off | Stream tokens as they arrive |
| System prompt | (editable) | Default security-focused prompt |

Use **Test connection** in Settings to verify Ollama is reachable and list available models.

## Building

```bash
./gradlew jar
```

JAR output: `build/libs/burp-ollama.jar`

## Testing

```bash
./gradlew test
```

Tests include:
- **OllamaResponseParser** – JSON parsing for tags and chat responses
- **OllamaService** – HTTP client behavior (mocked with MockWebServer)
- **OllamaConfig** – Settings persistence
- **SecurityPrompts** – Default prompt content

## License

MIT
