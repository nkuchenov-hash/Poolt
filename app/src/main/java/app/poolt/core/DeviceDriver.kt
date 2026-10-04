package app.poolt.core

interface DeviceDriver {
    val id: String
    val version: Int
    suspend fun discover(): List<Device>
    suspend fun pair(device: Device): Result<Unit>
    suspend fun send(device: Device, command: RemoteCommand): Result<Unit>
    suspend fun disconnect(device: Device) = Unit
}
