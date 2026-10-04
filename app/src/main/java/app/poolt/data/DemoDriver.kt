package app.poolt.data

import app.poolt.core.Device
import app.poolt.core.DeviceDriver
import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand

class DemoDriver : DeviceDriver {
    override val id = "poolt.demo"
    override val version = 1

    override suspend fun discover(): List<Device> = listOf(
        Device(
            id = "demo-tv",
            name = "Living Room TV",
            room = "Living room",
            type = DeviceType.TV,
            driverId = id,
            address = "192.168.1.42",
            isOnline = true
        )
    )

    override suspend fun pair(device: Device): Result<Unit> = Result.success(Unit)
    override suspend fun send(device: Device, command: RemoteCommand): Result<Unit> = Result.success(Unit)
}
