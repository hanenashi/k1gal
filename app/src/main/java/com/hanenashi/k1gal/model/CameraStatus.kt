package com.hanenashi.k1gal.model

internal sealed interface CameraStatus {
    data object Unchecked : CameraStatus
    data object Checking : CameraStatus
    data object Unreachable : CameraStatus

    data class Connected(
        val model: String?,
        val firmware: String?,
        val batteryPercent: Int?,
        val hot: Boolean,
        val slots: List<StorageSlot>,
    ) : CameraStatus {
        val activeSlot: StorageSlot? get() = slots.firstOrNull { it.active }
    }
}

internal data class StorageSlot(
    val name: String,
    val equipped: Boolean,
    val available: Boolean,
    val writable: Boolean,
    val active: Boolean,
    val remainingShots: Int?,
)
