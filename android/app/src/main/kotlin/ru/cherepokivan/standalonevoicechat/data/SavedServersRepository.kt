package ru.cherepokivan.standalonevoicechat.data

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class SavedServer(val id: String, val name: String, val host: String, val minecraftPort: Int, val voicePort: Int)

class SavedServersRepository(context: Context) {
    private val preferences = context.getSharedPreferences("saved_servers", Context.MODE_PRIVATE)

    fun getAll(): List<SavedServer> = runCatching {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(SavedServer(item.getString("id"), item.getString("name"), item.getString("host"), item.getInt("minecraftPort"), item.getInt("voicePort")))
            }
        }
    }.getOrDefault(emptyList())

    fun upsert(name: String, host: String, minecraftPort: Int, voicePort: Int): List<SavedServer> {
        val normalizedName = name.trim().ifBlank { host.trim() }
        val current = getAll().filterNot { it.host == host && it.voicePort == voicePort }
        val updated = current + SavedServer(UUID.randomUUID().toString(), normalizedName, host.trim(), minecraftPort, voicePort)
        save(updated)
        return updated
    }

    fun remove(id: String): List<SavedServer> = getAll().filterNot { it.id == id }.also(::save)

    private fun save(servers: List<SavedServer>) {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("host", server.host)
                put("minecraftPort", server.minecraftPort)
                put("voicePort", server.voicePort)
            })
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object { const val KEY = "servers" }
}
