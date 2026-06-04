package com.anomalyzed.simpletranscriber.updater

data class UpdateInfo(
    val updateAvailable: Boolean,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String?,
    val expectedSha256: String?
)
