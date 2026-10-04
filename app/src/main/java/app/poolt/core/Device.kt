package app.poolt.core

enum class DeviceType { TV, MEDIA_PLAYER, AUDIO, PROJECTOR, AIR_CONDITIONER, OTHER }

data class Device(
    val id: String,
    val name: String,
    val room: String? = null,
    val type: DeviceType,
    val driverId: String,
    val address: String? = null,
    val isOnline: Boolean = false
)

enum class RemoteCommand {
    POWER, HOME, BACK, UP, DOWN, LEFT, RIGHT, OK,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN, PLAY_PAUSE
}
