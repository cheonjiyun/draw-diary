package com.ssafy.drawdiary.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.ssafy.drawdiary.R
import com.ssafy.drawdiary.data.DrawThumb

private const val TAG = "CustomAdapter 싸피"

class ThumbAdapter(private val resource: Int, private val drawingList: List<DrawThumb>) :
    BaseAdapter() {

    override fun getCount(): Int {
        return drawingList.size
    }

    override fun getItem(position: Int): Any {
        return drawingList[position]
    }

    override fun getItemId(position: Int): Long {
        return drawingList[position].date.toString().toLong()
    }

    inner class Holder() {
        lateinit var imageView: ImageView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 매칭
        val view = if (convertView == null) {
            val inf = LayoutInflater.from(parent.context)
            Log.d(TAG, "inflate")
            Log.d(TAG, "parent: $parent")
            inf.inflate(resource, parent, false)
        } else {
            convertView
        }

        val holder = if (convertView == null) {
            val holder = Holder()
            holder.imageView = view.findViewById<ImageView>(R.id.thumb)

            Log.d(TAG, "findViewByid: $position")
            view.tag = holder
            holder

        } else {
            view.tag as Holder
        }

        Log.d(TAG, "getView: ")
        holder.imageView.setImageResource(R.drawable.ic_launcher_background)

        return view
    }
}