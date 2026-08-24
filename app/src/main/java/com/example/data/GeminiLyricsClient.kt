package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PartMoshi(val text: String)

@JsonClass(generateAdapter = true)
data class ContentMoshi(val parts: List<PartMoshi>)

@JsonClass(generateAdapter = true)
data class GenerateContentRequestMoshi(val contents: List<ContentMoshi>)

@JsonClass(generateAdapter = true)
data class CandidateMoshi(val content: ContentMoshi)

@JsonClass(generateAdapter = true)
data class GenerateContentResponseMoshi(val candidates: List<CandidateMoshi>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequestMoshi
    ): GenerateContentResponseMoshi
}

object GeminiLyricsClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    suspend fun generateSyncedLyrics(title: String, artist: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext """
                [00:00.00]Harmoni Player - Realtime Synced Lyrics
                [00:03.00]Mempersiapkan lirik tersinkronisasi...
                [00:06.00](API Key Gemini belum diset di panel Secrets)
                [00:10.00]Kemesraan dan harmoni melodi lagu $title
                [00:16.00]Dilantumkan syahdu oleh $artist
                [00:22.00]Mendukung format lossless FLAC/WAV premium
                [00:28.00]Kamu dapat mengedit tag metadata dan lirik secara manual!
                [00:35.00]Terima kasih telah menggunakan Harmoni Player.
            """.trimIndent()
        }

        val prompt = """
            Anda adalah asisten AI pembuat lirik tersinkronisasi real-time profesional.
            Tolong buatkan lirik lagu tersinkronisasi dalam format standar LRC (.lrc) untuk lagu dengan judul "$title" oleh penyanyi/artis "$artist".
            Pastikan formatnya adalah baris teks dengan format timestamp menit:detik di depannya, misalnya:
            [00:05.00]Mulai alunan musik indah...
            [00:11.30]Suatu hari ku duduk bersimpuh...
            [00:18.00]Melodi indah berdering...
            
            Persyaratan penting:
            1. Buat lirik penuh yang sinkron dari menit 00:00 hingga 01:30 dengan interval baris sekitar 4-8 detik.
            2. Tulis teks lirik dalam bahasa asli lagu tersebut (Indonesia atau Inggris).
            3. Kembalikan HANYA teks format LRC murni. JANGAN gunakan block code markdown ```lrc ... ```, jangan beri teks pendahuluan, jangan beri penjelasan apapun. Langsung mulai baris lirik pertama.
        """.trimIndent()

        val request = GenerateContentRequestMoshi(
            contents = listOf(ContentMoshi(
                parts = listOf(PartMoshi(text = prompt))
            ))
        )

        try {
            val response = service.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!responseText.isNullOrBlank()) {
                responseText.trim()
            } else {
                generateFallbackLyrics(title, artist)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            generateFallbackLyrics(title, artist)
        }
    }

    private fun generateFallbackLyrics(title: String, artist: String): String {
        return """
            [00:00.00]Memutar: $title
            [00:03.00]Artis: $artist
            [00:07.00](Gagal menghubungkan ke Gemini untuk lirik real-time)
            [00:12.00]Nikmati pemutaran audio lossless murni
            [00:18.00]Ubah tempo, atur band equalizer, atau pasang AB repeat di setelan.
        """.trimIndent()
    }
}
