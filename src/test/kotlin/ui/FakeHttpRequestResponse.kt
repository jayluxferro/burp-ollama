package ui

import burp.api.montoya.core.Annotations
import burp.api.montoya.core.ByteArray
import burp.api.montoya.core.Marker
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.handler.TimingData
import burp.api.montoya.http.message.ContentType
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import java.util.Optional

/**
 * Minimal HttpRequestResponse stub for testing outside Burp runtime.
 * Only implements methods needed by registries; the rest throw UnsupportedOperationException.
 */
class FakeHttpRequestResponse(
    private val method: String = "GET",
    private val url: String = "http://example.com/",
    private val requestBytes: kotlin.ByteArray = kotlin.ByteArray(0)
) : HttpRequestResponse {

    override fun request(): HttpRequest = fakeRequest

    override fun url(): String = url

    override fun response(): HttpResponse? = null

    override fun httpService(): HttpService = throw UnsupportedOperationException()
    override fun annotations(): Annotations = throw UnsupportedOperationException()
    override fun timingData(): Optional<TimingData> = throw UnsupportedOperationException()
    override fun hasResponse(): Boolean = throw UnsupportedOperationException()
    override fun contentType(): ContentType = throw UnsupportedOperationException()
    override fun statusCode(): Short = throw UnsupportedOperationException()
    override fun requestMarkers(): List<Marker> = throw UnsupportedOperationException()
    override fun responseMarkers(): List<Marker> = throw UnsupportedOperationException()
    override fun contains(text: String, caseSensitive: Boolean): Boolean = throw UnsupportedOperationException()
    override fun contains(pattern: java.util.regex.Pattern): Boolean = throw UnsupportedOperationException()
    override fun copyToTempFile(): HttpRequestResponse = throw UnsupportedOperationException()
    override fun withAnnotations(annotations: Annotations): HttpRequestResponse = throw UnsupportedOperationException()
    override fun withRequestMarkers(markers: List<Marker>): HttpRequestResponse = throw UnsupportedOperationException()
    override fun withRequestMarkers(vararg markers: Marker): HttpRequestResponse = throw UnsupportedOperationException()
    override fun withResponseMarkers(markers: List<Marker>): HttpRequestResponse = throw UnsupportedOperationException()
    override fun withResponseMarkers(vararg markers: Marker): HttpRequestResponse = throw UnsupportedOperationException()

    private inner class FakeHttpRequest : HttpRequest {
        override fun method(): String = this@FakeHttpRequestResponse.method
        override fun url(): String = this@FakeHttpRequestResponse.url

        override fun toByteArray(): ByteArray = object : ByteArray {
            override fun getBytes(): kotlin.ByteArray = this@FakeHttpRequestResponse.requestBytes
            override fun length(): Int = this@FakeHttpRequestResponse.requestBytes.size
            override fun getByte(index: Int): Byte = this@FakeHttpRequestResponse.requestBytes[index]
            override fun setByte(index: Int, value: Byte) { this@FakeHttpRequestResponse.requestBytes[index] = value }
            override fun setByte(index: Int, value: Int) { this@FakeHttpRequestResponse.requestBytes[index] = value.toByte() }
            override fun setBytes(index: Int, vararg values: Byte) { values.forEachIndexed { i, b -> this@FakeHttpRequestResponse.requestBytes[index + i] = b } }
            override fun setBytes(index: Int, vararg values: Int) { values.forEachIndexed { i, v -> this@FakeHttpRequestResponse.requestBytes[index + i] = v.toByte() } }
            override fun setBytes(index: Int, value: ByteArray) {
                for (i in 0 until value.length()) this@FakeHttpRequestResponse.requestBytes[index + i] = value.getByte(i)
            }
            override fun subArray(from: Int, to: Int): ByteArray = throw UnsupportedOperationException()
            override fun subArray(range: burp.api.montoya.core.Range): ByteArray = throw UnsupportedOperationException()
            override fun copy(): ByteArray = throw UnsupportedOperationException()
            override fun copyToTempFile(): ByteArray = throw UnsupportedOperationException()
            override fun indexOf(pattern: ByteArray): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: String): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: ByteArray, caseSensitive: Boolean): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: String, caseSensitive: Boolean): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: ByteArray, caseSensitive: Boolean, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: String, caseSensitive: Boolean, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: java.util.regex.Pattern): Int = throw UnsupportedOperationException()
            override fun indexOf(pattern: java.util.regex.Pattern, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: ByteArray): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: String): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: ByteArray, caseSensitive: Boolean): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: String, caseSensitive: Boolean): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: ByteArray, caseSensitive: Boolean, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: String, caseSensitive: Boolean, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: java.util.regex.Pattern): Int = throw UnsupportedOperationException()
            override fun countMatches(pattern: java.util.regex.Pattern, from: Int, to: Int): Int = throw UnsupportedOperationException()
            override fun withAppended(vararg bytes: Byte): ByteArray = throw UnsupportedOperationException()
            override fun withAppended(vararg ints: Int): ByteArray = throw UnsupportedOperationException()
            override fun withAppended(text: String): ByteArray = throw UnsupportedOperationException()
            override fun withAppended(bytes: ByteArray): ByteArray = throw UnsupportedOperationException()
            override fun iterator(): MutableIterator<Byte> = this@FakeHttpRequestResponse.requestBytes.toMutableList().iterator()
            override fun toString(): String = String(this@FakeHttpRequestResponse.requestBytes)
        }

        override fun isInScope(): Boolean = throw UnsupportedOperationException()
        override fun httpService(): HttpService = throw UnsupportedOperationException()
        override fun path(): String = throw UnsupportedOperationException()
        override fun query(): String = throw UnsupportedOperationException()
        override fun pathWithoutQuery(): String = throw UnsupportedOperationException()
        override fun fileExtension(): String = throw UnsupportedOperationException()
        override fun contentType(): ContentType = throw UnsupportedOperationException()
        override fun parameters(): List<burp.api.montoya.http.message.params.ParsedHttpParameter> = throw UnsupportedOperationException()
        override fun parameters(type: burp.api.montoya.http.message.params.HttpParameterType): List<burp.api.montoya.http.message.params.ParsedHttpParameter> = throw UnsupportedOperationException()
        override fun hasParameters(): Boolean = throw UnsupportedOperationException()
        override fun hasParameters(type: burp.api.montoya.http.message.params.HttpParameterType): Boolean = throw UnsupportedOperationException()
        override fun parameter(name: String, type: burp.api.montoya.http.message.params.HttpParameterType): burp.api.montoya.http.message.params.ParsedHttpParameter = throw UnsupportedOperationException()
        override fun parameterValue(name: String, type: burp.api.montoya.http.message.params.HttpParameterType): String = throw UnsupportedOperationException()
        override fun parameter(name: String): burp.api.montoya.http.message.params.ParsedHttpParameter = throw UnsupportedOperationException()
        override fun parameterValue(name: String): String = throw UnsupportedOperationException()
        override fun hasParameter(name: String, type: burp.api.montoya.http.message.params.HttpParameterType): Boolean = throw UnsupportedOperationException()
        override fun hasParameter(parameter: burp.api.montoya.http.message.params.HttpParameter): Boolean = throw UnsupportedOperationException()
        override fun hasHeader(header: burp.api.montoya.http.message.HttpHeader): Boolean = throw UnsupportedOperationException()
        override fun hasHeader(name: String): Boolean = throw UnsupportedOperationException()
        override fun hasHeader(name: String, value: String): Boolean = throw UnsupportedOperationException()
        override fun header(name: String): burp.api.montoya.http.message.HttpHeader = throw UnsupportedOperationException()
        override fun headerValue(name: String): String = throw UnsupportedOperationException()
        override fun headers(): List<burp.api.montoya.http.message.HttpHeader> = throw UnsupportedOperationException()
        override fun httpVersion(): String = throw UnsupportedOperationException()
        override fun bodyOffset(): Int = throw UnsupportedOperationException()
        override fun body(): ByteArray = throw UnsupportedOperationException()
        override fun bodyToString(): String = throw UnsupportedOperationException()
        override fun markers(): List<Marker> = throw UnsupportedOperationException()
        override fun contains(text: String, caseSensitive: Boolean): Boolean = throw UnsupportedOperationException()
        override fun contains(pattern: java.util.regex.Pattern): Boolean = throw UnsupportedOperationException()
        override fun copyToTempFile(): HttpRequest = throw UnsupportedOperationException()
        override fun withService(service: HttpService): HttpRequest = throw UnsupportedOperationException()
        override fun withPath(path: String): HttpRequest = throw UnsupportedOperationException()
        override fun withMethod(method: String): HttpRequest = throw UnsupportedOperationException()
        override fun withHeader(header: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withHeader(name: String, value: String): HttpRequest = throw UnsupportedOperationException()
        override fun withParameter(parameter: burp.api.montoya.http.message.params.HttpParameter): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedParameters(parameters: List<burp.api.montoya.http.message.params.HttpParameter>): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedParameters(vararg parameters: burp.api.montoya.http.message.params.HttpParameter): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedParameters(parameters: List<burp.api.montoya.http.message.params.HttpParameter>): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedParameters(vararg parameters: burp.api.montoya.http.message.params.HttpParameter): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedParameters(parameters: List<burp.api.montoya.http.message.params.HttpParameter>): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedParameters(vararg parameters: burp.api.montoya.http.message.params.HttpParameter): HttpRequest = throw UnsupportedOperationException()
        override fun withTransformationApplied(transformation: burp.api.montoya.http.message.requests.HttpTransformation): HttpRequest = throw UnsupportedOperationException()
        override fun withBody(body: String): HttpRequest = throw UnsupportedOperationException()
        override fun withBody(body: ByteArray): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedHeader(name: String, value: String): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedHeader(header: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedHeaders(headers: List<burp.api.montoya.http.message.HttpHeader>): HttpRequest = throw UnsupportedOperationException()
        override fun withAddedHeaders(vararg headers: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedHeader(name: String, value: String): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedHeader(header: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedHeaders(headers: List<burp.api.montoya.http.message.HttpHeader>): HttpRequest = throw UnsupportedOperationException()
        override fun withUpdatedHeaders(vararg headers: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedHeader(name: String): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedHeader(header: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedHeaders(headers: List<burp.api.montoya.http.message.HttpHeader>): HttpRequest = throw UnsupportedOperationException()
        override fun withRemovedHeaders(vararg headers: burp.api.montoya.http.message.HttpHeader): HttpRequest = throw UnsupportedOperationException()
        override fun withMarkers(markers: List<Marker>): HttpRequest = throw UnsupportedOperationException()
        override fun withMarkers(vararg markers: Marker): HttpRequest = throw UnsupportedOperationException()
        override fun withDefaultHeaders(): HttpRequest = throw UnsupportedOperationException()
        override fun toString(): String = "$method $url"
    }

    private val fakeRequest = FakeHttpRequest()
}
