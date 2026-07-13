package com.theveloper.pixelplay.data.backup.format

import com.google.gson.annotations.SerializedName
import com.theveloper.pixelplay.data.backup.model.DeviceInfo

data class ExternalBackup(
    @SerializedName("createdAt")
    val createdAt: String,

    @SerializedName("version")
    val version: String,

    @SerializedName("appVersion")
    val appVersion: String,

    @SerializedName("deviceInfo")
    val deviceInfo: ExternalDeviceInfo,

    @SerializedName("data")
    val data: ExternalBackupData
)

data class ExternalDeviceInfo(
    @SerializedName("model")
    val model: String,

    @SerializedName("systemVersion")
    val systemVersion: String
)

data class ExternalBackupData(
    @SerializedName("playHistory")
    val playHistory: List<ExternalPlayHistoryEntry>
)

data class ExternalPlayHistoryEntry(
    @SerializedName("id")
    val id: String,

    @SerializedName("playedDuration")
    val playedDuration: Double,

    @SerializedName("completed")
    val completed: Boolean,

    @SerializedName("artistName")
    val artistName: String,

    @SerializedName("songTitle")
    val songTitle: String,

    @SerializedName("sourceType")
    val sourceType: String,

    @SerializedName("albumName")
    val albumName: String,

    @SerializedName("playedAt")
    val playedAt: String,

    @SerializedName("duration")
    val duration: Int,

    @SerializedName("songId")
    val songId: String
)
