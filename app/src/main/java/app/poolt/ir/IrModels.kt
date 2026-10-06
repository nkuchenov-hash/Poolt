package app.poolt.ir

import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand

enum class IrProtocolType { NEC, NEC_EXT, SAMSUNG32, SONY12, SONY15, SONY20, RAW }

data class IrCatalogEntry(
    val path: String,
    val brand: String,
    val deviceType: String,
    val codeSet: String
)

data class IrProfile(
    val id: String,
    val brand: String,
    val model: String,
    val deviceType: DeviceType,
    val frequency: Int,
    val protocol: IrProtocolType,
    val address: Int = 0,
    val subAddress: Int = -1,
    val commands: Map<RemoteCommand, Int> = emptyMap()
)
