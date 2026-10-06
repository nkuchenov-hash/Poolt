package app.poolt.core

enum class DeviceType {
    TV, SET_TOP_BOX, MEDIA_PLAYER, AUDIO, SOUNDBAR, PROJECTOR,
    AIR_CONDITIONER, FAN, AIR_PURIFIER, CAMERA, GAME_CONSOLE,
    LIGHTING, OTHER
}

data class Device(
    val id: String,
    val name: String,
    val room: String? = null,
    val type: DeviceType,
    val driverId: String,
    val brand: String? = null,
    val model: String? = null,
    val address: String? = null,
    val isOnline: Boolean = false
)

enum class RemoteCommand {
    POWER, SOURCE, SETTINGS, HOME, BACK, MENU, GUIDE, INFO,
    UP, DOWN, LEFT, RIGHT, OK,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    CHANNEL_UP, CHANNEL_DOWN,
    PLAY_PAUSE, REWIND, FAST_FORWARD,
    NUMBER_0, NUMBER_1, NUMBER_2, NUMBER_3, NUMBER_4,
    NUMBER_5, NUMBER_6, NUMBER_7, NUMBER_8, NUMBER_9
}
