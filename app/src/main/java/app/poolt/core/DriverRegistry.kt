package app.poolt.core

class DriverRegistry(drivers: List<DeviceDriver>) {
    private val byId = drivers.associateBy { it.id }

    fun driverFor(device: Device): DeviceDriver? = byId[device.driverId]

    fun installedDrivers(): List<DeviceDriver> = byId.values.sortedBy { it.id }

    suspend fun discoverAll(): List<Device> =
        byId.values.flatMap { driver ->
            runCatching { driver.discover() }.getOrDefault(emptyList())
        }.distinctBy { it.id }
}
