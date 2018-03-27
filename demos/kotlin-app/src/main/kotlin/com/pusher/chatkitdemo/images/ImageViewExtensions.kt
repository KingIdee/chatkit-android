package com.pusher.chatkitdemo.images

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.pusher.chatkitdemo.R

var ImageView.imageUrl : CharSequence
    get() = getTag(R.id.image_url) as String
    set(value) {
        Glide.with(this).load(value).into(this)
        setTag(R.id.image_url, value)
    }
