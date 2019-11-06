package com.google.firebase.udacity.rifat.friendlychat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

import com.bumptech.glide.Glide

class MessageAdapter
internal constructor(context: Context, resource: Int, objects: List<FriendlyMessage>)
    : ArrayAdapter<FriendlyMessage>(context, resource, objects) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var itemView = view
        if (itemView == null) {
            itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message, parent, false)
        }

        val photoImageView = itemView!!.findViewById<ImageView>(R.id.photoImageView)
        val messageTextView = itemView.findViewById<TextView>(R.id.messageTextView)
        val authorTextView = itemView.findViewById<TextView>(R.id.nameTextView)

        val message = getItem(position)

        val isPhoto = message!!.photoUrl != null

        if (isPhoto) {
            messageTextView.visibility = View.GONE
            photoImageView.visibility = View.VISIBLE
            Glide.with(photoImageView.context)
                    .load(message.photoUrl)
                    .into(photoImageView)
        } else {
            messageTextView.visibility = View.VISIBLE
            photoImageView.visibility = View.GONE
            messageTextView.text = message.text
        }

        authorTextView.text = message.name

        return itemView
    }
}
