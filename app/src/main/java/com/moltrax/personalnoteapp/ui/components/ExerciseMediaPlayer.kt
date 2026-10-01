package com.moltrax.personalnoteapp.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.moltrax.personalnoteapp.R
import okhttp3.OkHttpClient
import java.io.File

/**
 * Egzersiz demo medyasını oynatan ortak bileşen.
 *
 * - Gerçek videolar (.mp4/.webm/.m3u8 ...) AndroidX Media3 **ExoPlayer** ile oynatılır (döngülü).
 * - ExerciseDB animasyonlu **GIF**'leri ExoPlayer'ın oynatamadığı için Coil ile gösterilir.
 *
 * [source] hem lokal dosya yolu (çevrimdışı indirilmiş) hem de uzak URL olabilir; ikisi de
 * desteklenir. Boş/null ise küçük bir bilgi metni gösterilir.
 */
@Composable
fun ExerciseMediaPlayer(
    source: String?,
    modifier: Modifier = Modifier,
    heightDp: Int = 200,
    exerciseDbKey: String = "",
) {
    val shaped = modifier
        .fillMaxWidth()
        .height(heightDp.dp)
        .clip(RoundedCornerShape(12.dp))

    if (source.isNullOrBlank()) {
        Box(shaped, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.exercise_no_demo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val isRemote = source.startsWith("http", ignoreCase = true)
    val cleanPath = source.substringBefore('?')
    // ExerciseDB demo'ları GIF'tir. Lokal dosyalarda uzantı (.gif), uzak adreste ise resim ucu
    // (".../image") GIF olarak değerlendirilir (uzak URL'de uzantı yoktur).
    val isGif = cleanPath.endsWith(".gif", ignoreCase = true) ||
        cleanPath.endsWith("/image", ignoreCase = true)

    if (isGif) {
        GifPlayer(if (isRemote) source else File(source), shaped, exerciseDbKey)
    } else {
        ExoVideoPlayer(if (isRemote) Uri.parse(source) else Uri.fromFile(File(source)), shaped)
    }
}

/** Coil ImageLoader (GIF kod çözücüyle) animasyonlu demo gösterir. */
@Composable
private fun GifPlayer(data: Any, modifier: Modifier, exerciseDbKey: String) {
    val context = LocalContext.current
    val imageLoader = rememberGifImageLoader(exerciseDbKey)
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        imageLoader = imageLoader,
        contentDescription = stringResource(R.string.exercise_demo_desc),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * Liste satırlarında (ör. arama sonuçları) kullanılan küçük kare demo önizlemesi. GIF'ler de
 * animasyonlu görünür. [source] lokal dosya yolu veya uzak URL olabilir; boş/null ise yer tutucu
 * bir ikon gösterilir.
 */
@Composable
fun ExerciseThumb(
    source: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48,
    exerciseDbKey: String = "",
) {
    val box = modifier
        .size(sizeDp.dp)
        .clip(RoundedCornerShape(8.dp))

    if (source.isNullOrBlank()) {
        Box(
            box.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = stringResource(R.string.exercise_demo_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((sizeDp / 2).dp),
            )
        }
        return
    }

    val context = LocalContext.current
    val data: Any = if (source.startsWith("http", ignoreCase = true)) source else File(source)
    AsyncImage(
        model = ImageRequest.Builder(context).data(data).crossfade(true).build(),
        imageLoader = rememberGifImageLoader(exerciseDbKey),
        contentDescription = stringResource(R.string.exercise_demo_desc),
        contentScale = ContentScale.Crop,
        modifier = box,
    )
}

/**
 * GIF kod çözücülü Coil ImageLoader'ı hatırlar (SDK 28+ ImageDecoder, altı GifDecoder).
 * ExerciseDB demo GIF'leri `…/image` ucundan gelir ve `X-RapidAPI-Key` header'ı ister; bu yüzden
 * yükleyiciye RapidAPI host'ları için anahtarı ekleyen bir OkHttp interceptor'ı verilir.
 */
@Composable
private fun rememberGifImageLoader(exerciseDbKey: String): ImageLoader {
    val context = LocalContext.current
    return remember(exerciseDbKey) {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val needsKey = req.url.host.endsWith("rapidapi.com", ignoreCase = true) &&
                    exerciseDbKey.isNotBlank()
                val finalReq = if (needsKey)
                    req.newBuilder().header("X-RapidAPI-Key", exerciseDbKey).build()
                else req
                chain.proceed(finalReq)
            }
            .build()
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}

/** Media3 ExoPlayer ile gerçek videoyu döngüsel olarak oynatır; bileşen yok olunca serbest bırakır. */
@OptIn(UnstableApi::class)
@Composable
private fun ExoVideoPlayer(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier,
    )
}
