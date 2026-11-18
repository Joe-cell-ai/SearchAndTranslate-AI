/* Copyright (C) 2025 AKS-Labs. GPL-3.0-or-later. */
package com.fuzzyfly0.searchandtranslate.data

import android.content.Context
import androidx.annotation.StringRes
import com.fuzzyfly0.searchandtranslate.R

sealed class SearchEngine(@StringRes val displayNameRes: Int) {
    object Google : SearchEngine(R.string.engine_google)
    object Bing : SearchEngine(R.string.engine_bing)
    object TinEye : SearchEngine(R.string.engine_tineye)
    object Search4Faces : SearchEngine(R.string.engine_search4faces)
    object Yandex : SearchEngine(R.string.engine_yandex)
    object FaceCheck : SearchEngine(R.string.engine_facecheck)
    object Kairos : SearchEngine(R.string.engine_kairos)
    object GithubImageResearch : SearchEngine(R.string.engine_github_image_research)
    object ThreadReader : SearchEngine(R.string.engine_threadreader)
    object OsintTools : SearchEngine(R.string.engine_osint_tools)
    object Faceagle : SearchEngine(R.string.engine_faceagle)
    object PicDetective : SearchEngine(R.string.engine_picdetective)

    companion object {
        fun values(): List<SearchEngine> = listOf(
            Google, Bing, TinEye, Search4Faces, Yandex, FaceCheck, Kairos,
            GithubImageResearch, ThreadReader, OsintTools, Faceagle, PicDetective,
        )
    }

    fun getDisplayName(context: Context): String = context.getString(displayNameRes)

    val name: String get() = when (this) {
        Google -> "Google"
        Bing -> "Bing"
        TinEye -> "TinEye"
        Search4Faces -> "Search4Faces"
        Yandex -> "Yandex"
        FaceCheck -> "FaceCheck"
        Kairos -> "Kairos"
        GithubImageResearch -> "GitHub Image Research"
        ThreadReader -> "ThreadReader"
        OsintTools -> "OSINT Tools"
        Faceagle -> "Faceagle"
        PicDetective -> "PicDetective"
    }

    /** New providers open their own site; users choose whether to upload an image there. */
    val website: String? get() = when (this) {
        Search4Faces -> "https://search4faces.com/"
        Yandex -> "https://yandex.com/images/"
        FaceCheck -> "https://facecheck.id/"
        Kairos -> "https://www.kairos.com/demo"
        GithubImageResearch -> "https://github.com/search?q=Image-Research&type=repositories"
        ThreadReader -> "https://threadreaderapp.com/"
        OsintTools -> "https://cipher387.github.io/osint_stuff_tool_collection/"
        Faceagle -> "https://faceagle.com/"
        PicDetective -> "https://picdetective.com/"
        else -> null
    }
}

val SearchEngine.isDirectUpload: Boolean
    get() = false
