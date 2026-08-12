package com.bittv.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.data.Channel
import com.bittv.iptv.util.LogoLoader

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onFavoriteClick: (Channel) -> Unit,
    private val isFavorite: (Channel) -> Boolean
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {
    private val items = mutableListOf<Channel>()

    fun submitList(channels: List<Channel>) {
        items.clear()
        items.addAll(channels)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): Channel = items[position]
    fun currentItems(): List<Channel> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val name: TextView = itemView.findViewById(R.id.channelName)
        private val group: TextView = itemView.findViewById(R.id.channelGroup)
        private val favorite: ImageButton = itemView.findViewById(R.id.favoriteButton)

        fun bind(channel: Channel) {
            name.text = channel.name
            group.text = channel.group
            LogoLoader.load(channel.logoUrl, logo)
            favorite.setImageResource(if (isFavorite(channel)) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            itemView.setOnClickListener { onChannelClick(channel) }
            favorite.setOnClickListener { onFavoriteClick(channel) }
        }
    }
}