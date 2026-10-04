package app.poolt.core

class DriverRegistry(drivers: List<DeviceDriver>) {
    private val byProtocol = drivers.associateBy { it.protocol }
    fun driverFor(device: Device): DeviceDriver? = byProtocol[device.protocol]

    suspend fun discoverAll(): List<Device> =
        byProtocol.values.flatMap { driver ->
            runCatching { driver.discover() }.getOrDefault(emptyList())
        }.distinctBy { it.id }
}
