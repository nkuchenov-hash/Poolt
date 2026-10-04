package app.poolt.core

interface DeviceDriver {
    val protocol: Protocol
    suspend fun discover(): List<Device>
    suspend fun pair(device: Device): Result<Unit>
    suspend fun send(device: Device, command: RemoteCommand): Result<Unit>
    suspend fun disconnect(device: Device) = Unit
}
