package com.fuzzyfly0.searchandtranslate.ai.restoration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorationModelTest {
    private val valid = RestorationModel(
        id = "realesrgan-x4",
        displayName = "Real-ESRGAN x4",
        task = RestorationTask.IMAGE_UPSCALING,
        downloadUrl = "https://models.example.org/realesrgan-x4.onnx",
        sha256 = "a".repeat(64),
        maxBytes = 200_000_000L,
    )

    @Test fun acceptsHttpsModelWithChecksumAndBoundedSize() {
        assertTrue(valid.validate())
    }

    @Test fun rejectsHttpDownloads() {
        assertFalse(valid.copy(downloadUrl = "http://models.example.org/model.onnx").validate())
    }

    @Test fun rejectsMissingChecksum() {
        assertFalse(valid.copy(sha256 = "not-a-checksum").validate())
    }

    @Test fun rejectsPathLikeModelIds() {
        assertFalse(valid.copy(id = "../outside").validate())
    }

    @Test fun rejectsOversizedModelLimit() {
        assertFalse(valid.copy(maxBytes = RestorationModel.MAX_MODEL_BYTES + 1).validate())
    }
}
