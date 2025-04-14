package com.ssafy.drawdiary.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.ssafy.drawdiary.R
import com.ssafy.drawdiary.data.DrawThumb

private const val TAG = "CustomAdapter 싸피"

class ThumbAdapter(private val resource: Int, private val drawingList: List<DrawThumb>) :
    RecyclerView.Adapter<ThumbAdapter.Holder>() {

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var imageView: ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
    }

    override fun getItemCount(): Int {
        return drawingList.size
    }
}