package com.sentinel.bridge.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a recorded device capability profile.
 *
 * A capability profile captures the state of the Xiaomi Recorder UI and device
 * environment at a point in time. It is used to detect when a system update changes
 * the Recorder UI, triggering a [CAPABILITY_MISMATCH] broadcast instead of silent failure.
 *
 * @property id Auto-generated primary key.
 * @property version Schema version for this profile format.
 * @property recorderPackage Package name of the Xiaomi Recorder app.
 * @property recorderVersion Version string of the installed Recorder app.
 * @property hyperOsVersion HyperOS version detected via `getprop ro.mi.os.version.name`.
 * @property availableNodes JSON array of accessibility node descriptions found during inspection.
 * @property recordedAt Epoch milliseconds when this profile was captured.
 */
@Entity(tableName = "capability_profiles")
data class CapabilityProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: Int,
    val recorderPackage: String,
    val recorderVersion: String,
    val hyperOsVersion: String,
    val availableNodes: String,
    val recordedAt: Long
)
