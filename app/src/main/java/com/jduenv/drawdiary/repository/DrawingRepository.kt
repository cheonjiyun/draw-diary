package com.jduenv.drawdiary.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jduenv.drawdiary.R
import com.jduenv.drawdiary.customView.StrokeData
import com.jduenv.drawdiary.data.DrawThumb
import com.jduenv.drawdiary.data.DrawingInfo
import com.jduenv.drawdiary.util.DateUtil.parseTimestampFromEntryName
import java.io.File
import java.io.FileOutputStream

private const val TAG = "DrawingRepository"

class DrawingRepository() {

    // 저장
    fun saveFinal(baseDir: File, entryName: String, bitmap: Bitmap) =
        savePng(File(baseDir, entryName), FILE_FINAL, bitmap)

    fun saveFill(baseDir: File, entryName: String, bitmap: Bitmap) =
        savePng(File(baseDir, entryName), FILE_FILL, bitmap)

    fun saveStrokes(baseDir: File, entryName: String, strokes: List<StrokeData>) =
        saveJson(File(baseDir, entryName), FILE_STROKES, Gson().toJson(strokes))

    fun saveInfo(baseDir: File, entryName: String, info: DrawingInfo) =
        saveJson(File(baseDir, entryName), FILE_INFO, Gson().toJson(info))

    // 로드
    fun loadFinalBitmap(baseDir: File, entryName: String) =
        loadPng(File(baseDir, entryName), FILE_FINAL)

    fun loadFillBitmap(baseDir: File, entryName: String) =
        loadPng(File(baseDir, entryName), FILE_FILL)

    fun loadStrokes(baseDir: File, entryName: String): List<StrokeData>? =
        loadJson(File(baseDir, entryName), FILE_STROKES)

    fun loadInfo(baseDir: File, entryName: String): DrawingInfo? =
        loadJson(File(baseDir, entryName), FILE_INFO)

    /**
     * 저장된 모든 엔트리 폴더에서 final.png의 경로, 제목, 수정일을 읽어옵니다.
     *
     * @param baseDir 앱의 filesDir, 즉 엔트리 폴더들이 모여있는 상위 디렉토리
     * @return 각 엔트리의 DrawThumb 리스트
     */
    fun loadAllFinalThumbs(baseDir: File): List<DrawThumb> {
        return baseDir.listFiles()
            // 1) 엔트리 폴더만 필터링
            ?.filter { it.isDirectory }
            // 2) 각 폴더에서 final.png와 info.json을 읽어 DrawThumb 생성
            ?.mapNotNull { folder ->
                val entryName = folder.name
                val dir = File(baseDir, entryName)

                val finalFile = File(dir, FILE_FINAL)
                if (!finalFile.exists()) return@mapNotNull null

                val info = loadInfo(baseDir, entryName)
                val title = info?.title ?: entryName
                val data = info?.date ?: "날짜오류"

                DrawThumb(
                    entryName = entryName,
                    path = finalFile.absolutePath,
                    title = title,
                    date = data
                )
            }
            // 3) 최신순 정렬 (date > 생성일)
            ?.sortedWith(
                compareByDescending<DrawThumb> { it.date }
                    .thenByDescending { parseTimestampFromEntryName(it.entryName) }
            )


            ?: emptyList()
    }

    fun deleteDirectory(baseDir: File, entryName: String): Boolean {
        val entryDir = File(baseDir, entryName)
        return entryDir.deleteRecursively()
    }


    fun downLoad(context: Context, baseDir: File, entryName: String, info: DrawingInfo): Boolean {
        val srcFile = File(baseDir, "$entryName/final.png")
        if (!srcFile.exists()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상
            val filename = "${entryName}_${info.title}_${info.date}.png"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + context.getString(R.string.app_name)
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, contentValues)

            if (itemUri != null) {
                resolver.openOutputStream(itemUri).use { outputStream ->
                    srcFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

            }

        } else {
            // Android 9 이하
            val picturesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val myFolder = File(picturesDir, context.getString(R.string.app_name))
            if (!myFolder.exists()) {
                myFolder.mkdirs()
            }

            val destFile = File(myFolder, "${entryName}_${info.title}_${info.date}.png")
            srcFile.copyTo(destFile, overwrite = true)

            // MediaScanner로 갤러리에 반영
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/png"),
                null
            )

        }
        return true
    }

    // ======= private =======

    private fun savePng(filesDir: File, entryName: String, bitmap: Bitmap): Boolean {
        return try {
            FileOutputStream(File(filesDir, entryName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveJson(filesDir: File, entryName: String, json: String): Boolean {
        return try {
            File(filesDir, entryName).writeText(json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    private fun loadPng(filesDir: File, fileName: String): Bitmap {
        val file = File(filesDir, fileName)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            Log.e(TAG, "loadPng: 파일이 존재하지 않습니다. ${file.absolutePath}")
            Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
    }

    private inline fun <reified T> loadJson(filesDir: File, fileName: String): T? {
        val file = File(filesDir, fileName)
        if (!file.exists()) {
            Log.e(TAG, "loadJson: 파일이 존재하지 않습니다. ${file.absolutePath}")
            return null
        }
        return try {
            val json = file.readText()
            val type = object : TypeToken<T>() {}.type
            Gson().fromJson<T>(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val FILE_FINAL = "final.png"
        private const val FILE_FILL = "fill.png"
        private const val FILE_STROKES = "strokes.json"
        private const val FILE_INFO = "info.json"
    }

}