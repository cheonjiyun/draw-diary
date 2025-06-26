package com.jduenv.drawdiary.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jduenv.drawdiary.customView.StrokeData
import java.io.File
import java.io.FileOutputStream

private const val TAG = "DrawingRepository"

class DrawingRepository() {
    /** JSON 으로 획 리스트 저장 */
    fun saveStrokes(filesDir: File, entryName: String, strokes: List<StrokeData>): Boolean {
        return try {
            val json = Gson().toJson(strokes)
            File(filesDir, "$entryName.json").writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Bitmap 을 PNG 로 저장 */
    fun saveImage(filesDir: File, entryName: String, bitmap: Bitmap): Boolean {
        return try {
            FileOutputStream(File(filesDir, "${entryName}.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 저장된 JSON 파일에서 StrokeData 리스트를 읽어옵니다.
     * @return 파일이 없거나 파싱 실패 시 null
     */
    fun loadStrokes(filesDir: File, entryName: String): List<StrokeData>? {
        val file = File(filesDir, "$entryName.json")
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<StrokeData>>() {}.type
            Gson().fromJson<List<StrokeData>>(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadFillBitmap(filesDir: File, entryName: String): Bitmap {
        val file = File(filesDir, "${entryName}_fill.png")

        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            // 로그 출력
            Log.e(TAG, "fill bitmap not found: ${file.absolutePath}")

            // 기본 흰색 비트맵 생성 (예: 1080x1920 사이즈, 필요 시 조정)
            Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
    }

    fun loadFinalBitmap(filesDir: File, entryName: String): Bitmap {
        // 파일 경로에서 비트맵 로드
        val file = File(filesDir, "${entryName}_final.png")

        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            // 기본 흰색 비트맵 생성 (예: 1080x1920 사이즈, 필요 시 조정)
            Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
    }
}