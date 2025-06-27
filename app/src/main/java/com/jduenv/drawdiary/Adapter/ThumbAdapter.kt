package com.jduenv.drawdiary.Adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.data.DrawThumb

class ThumbAdapter(
    private val drawingList: MutableList<DrawThumb>,
    private val onItemClick: (DrawThumb) -> Unit
) :
    RecyclerView.Adapter<ThumbAdapter.Holder>() {

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.thumb)
        private val textViewDate: TextView = itemView.findViewById(R.id.date)
        private val textViewTitle: TextView = itemView.findViewById(R.id.title)

        fun bind(thumb: DrawThumb) {
            // 파일 경로에서 비트맵 로드
            val bitmap = BitmapFactory.decodeFile(thumb.path)
            imageView.setImageBitmap(bitmap)

            // 클릭 시 콜백
            itemView.setOnClickListener {
                onItemClick(thumb)
            }

            textViewDate.text = thumb.date
            textViewTitle.text = thumb.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(drawingList[position])
    }

    override fun getItemCount(): Int {
        return drawingList.size
    }

    /** 새 데이터로 리스트를 교체하고 화면 갱신 */
    fun updateData(newList: List<DrawThumb>) {
        drawingList.clear()
        drawingList.addAll(newList)
        notifyDataSetChanged()
    }
}