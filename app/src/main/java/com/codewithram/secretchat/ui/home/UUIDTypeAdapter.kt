package com.codewithram.secretchat.ui.home

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.UUID

class UUIDTypeAdapter : TypeAdapter<UUID>() {
    override fun write(out: JsonWriter, value: UUID?) {
        out.value(value?.toString())
    }

    override fun read(`in`: JsonReader): UUID {
        return UUID.fromString(`in`.nextString())
    }
}
