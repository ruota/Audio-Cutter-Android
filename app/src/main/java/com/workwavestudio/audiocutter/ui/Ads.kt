package com.workwavestudio.audiocutter.ui

import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.workwavestudio.audiocutter.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.BANNER_AD_UNIT_ID
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    MATCH_PARENT,
                    MATCH_PARENT
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("AdsBanner", "Banner loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdsBanner", "Banner failed: ${error.code} ${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
