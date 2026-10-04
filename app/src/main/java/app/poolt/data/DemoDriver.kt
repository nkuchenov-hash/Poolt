package app.poolt.data

import app.poolt.core.*

class DemoDriver : DeviceDriver {
    override val protocol = Protocol.GENERIC

    override suspend fun discover(): List<Device> = listOf(
        Device(
            id = "demo-tv",
            name = "Living Room TV",
            room = "Living room",
            type = DeviceType.TV,
            protocol = Protocol.GENERIC,
            address = "192.168.1.42",
            isOnline = true
        )
    )

    override suspend fun pair(device: Device): Result<Unit> = Result.success(Unit)
    override suspend fun send(device: Device, command: RemoteCommand): Result<Unit> = Result.success(Unit)
}
