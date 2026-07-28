package ollama

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OllamaErrorFormatterTest {

    @Test
    fun `model not found error returns message about pulling the model`() {
        val error = OllamaException("model \"llama3.2:3b\" not found")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("not installed"))
        assertTrue(result.contains("ollama pull llama3.2:3b"))
    }

    @Test
    fun `connection refused error returns message about starting Ollama`() {
        val error = OllamaException("Connection refused: /127.0.0.1:11434")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("Cannot connect to Ollama"))
        assertTrue(result.contains("ollama serve"))
        assertTrue(result.contains("http://localhost:11434"))
    }

    @Test
    fun `connection reset error also triggers connection message`() {
        val error = OllamaException("Connection reset by peer")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("Cannot connect to Ollama"))
    }

    @Test
    fun `no route to host error returns connection message`() {
        val error = OllamaException("No route to host")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("Cannot connect to Ollama"))
    }

    @Test
    fun `unknown host error returns message about checking URL`() {
        val error = OllamaException("Unknown host: nonexistent.example.com")
        val result = OllamaErrorFormatter.format(error, "http://nonexistent.example.com", "llama3.2:3b")
        assertTrue(result.contains("Cannot resolve host"))
        assertTrue(result.contains("http://nonexistent.example.com"))
    }

    @Test
    fun `nodename nor servname provided returns host resolution message`() {
        val error = OllamaException("nodename nor servname provided, or not known")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("Cannot resolve host"))
    }

    @Test
    fun `timeout error returns message about increasing timeout`() {
        val error = OllamaException("timeout: socket timed out")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("timed out"))
        assertTrue(result.contains("Increase timeout"))
    }

    @Test
    fun `timed out phrase also triggers timeout message`() {
        val error = OllamaException("timed out waiting for response")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("timed out"))
    }

    @Test
    fun `generic error returns clean message without raw JSON`() {
        val error = OllamaException("Something went wrong")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertTrue(result.contains("Something went wrong"))
        assertTrue(result.contains("http://localhost:11434"))
    }

    @Test
    fun `HTTP error with JSON body has raw JSON stripped`() {
        val error = OllamaException("Ollama returned 500: {\"error\":\"internal error\"}")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertFalse(result.contains("\"error\""))
        assertTrue(result.contains("Ollama returned an error"))
        assertTrue(result.contains("http://localhost:11434"))
    }

    @Test
    fun `Chat failed prefix is stripped from generic error`() {
        val error = OllamaException("Chat failed: Model not responding")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "llama3.2:3b")
        assertFalse(result.contains("Chat failed:"))
        assertTrue(result.contains("Model not responding"))
    }

    @Test
    fun `error with nested JSON error extraction`() {
        val error = OllamaException("Chat failed: something: {\"error\":\"model 'codellama' not found\"}")
        val result = OllamaErrorFormatter.format(error, "http://localhost:11434", "codellama")
        assertTrue(result.contains("not installed"))
        assertTrue(result.contains("ollama pull codellama"))
    }
}
