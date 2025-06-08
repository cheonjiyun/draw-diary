package com.jduenv.drawdiary.Adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.data.DrawThumb

private const val TAG = "CustomAdapter 싸피"

class ThumbAdapter(
    private val resource: Int,
    private val drawingList: MutableList<DrawThumb>,
    private val onItemClick: (DrawThumb) -> Unit
) :
    RecyclerView.Adapter<ThumbAdapter.Holder>() {

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.thumb)

        fun bind(thumb: DrawThumb) {
            // 파일 경로에서 비트맵 로드
            val bitmap = BitmapFactory.decodeFile(thumb.path)
            imageView.setImageBitmap(bitmap)

            // 클릭 시 콜백
            itemView.setOnClickListener {
                onItemClick(thumb)
            }
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