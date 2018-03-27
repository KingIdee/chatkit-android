package com.pusher.chatkitdemo.images

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.pusher.chatkitdemo.R

var ImageView.imageUrl: CharSequence?
    get() = image?.url
    set(value) {
        val url = checkNotNull(value) { "imageUrl must not be null" }
        image = url.withOptions { /* No options */ }
    }

var ImageView.image: ImageRequest?
    get() = getTag(R.id.image_request) as? ImageRequest
    set(value) {
        val request = checkNotNull(value) { "image must not be null" }
        val requestOptions = RequestOptions()
        request.options.decorations.forEach { decoration ->
            when (decoration) {
                is ImageOptions.Decoration.Circle -> requestOptions.transform(CircleCrop())
            }
        }
        Glide.with(this@image).load(request.url).apply(requestOptions).into(this@image)
    }

fun CharSequence.withOptions(block: ImageOptions.() -> Unit): ImageRequest =
    ImageRequest(this, ImageOptions().apply(block))

class ImageRequest(val url: CharSequence, val options: ImageOptions)

data class ImageOptions(
    val decorations: MutableList<Decoration> = mutableListOf()
) {

    sealed class Decoration {
        object Circle : Decoration()
    }

}
