package app.poolt.ir

import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand

enum class IrProtocolType { NEC, SAMSUNG32, RAW }

data class IrProfile(
    val id: String,
    val brand: String,
    val model: String,
    val deviceType: DeviceType,
    val frequency: Int,
    val protocol: IrProtocolType,
    val address: Int = 0,
    val commands: Map<RemoteCommand, Int> = emptyMap()
)
