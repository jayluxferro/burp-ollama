package ollama

import burp.api.montoya.persistence.Preferences

/**
 * In-memory Preferences implementation for testing.
 */
class FakePreferences : Preferences {

    private val strings = mutableMapOf<String, String>()
    private val integers = mutableMapOf<String, Int>()
    private val booleans = mutableMapOf<String, Boolean>()

    override fun getString(key: String): String? = strings[key]
    override fun setString(key: String, value: String) { strings[key] = value }
    override fun deleteString(key: String) { strings.remove(key) }
    override fun stringKeys(): Set<String> = strings.keys.toSet()

    override fun getInteger(key: String): Int? = integers[key]
    override fun setInteger(key: String, value: Int) { integers[key] = value }
    override fun deleteInteger(key: String) { integers.remove(key) }
    override fun integerKeys(): Set<String> = integers.keys.toSet()

    override fun getBoolean(key: String): Boolean? = booleans[key]
    override fun setBoolean(key: String, value: Boolean) { booleans[key] = value }
    override fun deleteBoolean(key: String) { booleans.remove(key) }
    override fun booleanKeys(): Set<String> = booleans.keys.toSet()

    override fun getByte(key: String): Byte? = null
    override fun setByte(key: String, value: Byte) {}
    override fun deleteByte(key: String) {}
    override fun byteKeys(): Set<String> = emptySet()

    override fun getShort(key: String): Short? = null
    override fun setShort(key: String, value: Short) {}
    override fun deleteShort(key: String) {}
    override fun shortKeys(): Set<String> = emptySet()

    override fun getLong(key: String): Long? = null
    override fun setLong(key: String, value: Long) {}
    override fun deleteLong(key: String) {}
    override fun longKeys(): Set<String> = emptySet()
}
