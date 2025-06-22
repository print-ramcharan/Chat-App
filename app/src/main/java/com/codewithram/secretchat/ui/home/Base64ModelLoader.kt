package com.codewithram.secretchat.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.ByteArrayInputStream
import java.io.InputStream

class Base64ModelLoader : ModelLoader<String, InputStream> {
    override fun buildLoadData(model: String, width: Int, height: Int, options: com.bumptech.glide.load.Options): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(ObjectKey(model), Base64Fetcher(model))
    }

    override fun handles(model: String): Boolean {
        return model.startsWith("data:image") || model.length > 100
    }

    class Base64Fetcher(private val base64String: String) : DataFetcher<InputStream> {
        private var stream: InputStream? = null

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            try {
                val pureBase64 = base64String.substringAfter(",")
                val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                stream = ByteArrayInputStream(decodedBytes)
                callback.onDataReady(stream)
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() {
            stream?.close()
        }

        override fun cancel() {}
        override fun getDataClass() = InputStream::class.java
        override fun getDataSource() = DataSource.LOCAL
    }

    class Factory : ModelLoaderFactory<String, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return Base64ModelLoader()
        }

        override fun teardown() {}
    }
}
