package app.poolt.drivers

import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand

enum class DriverTransport {
    HTTP,
    TCP,
    UDP,
    WEBSOCKET,
    IR,
    NATIVE
}

data class DriverPackage(
    val id: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val minAppVersion: Int,
    val deviceType: DeviceType,
    val transport: DriverTransport,
    val discovery: DiscoveryDefinition?,
    val commands: Map<RemoteCommand, CommandDefinition>
)

data class DiscoveryDefinition(
    val type: String,
    val query: String?,
    val port: Int?
)

data class CommandDefinition(
    val method: String?,
    val path: String?,
    val body: String?,
    val payload: String?
)
