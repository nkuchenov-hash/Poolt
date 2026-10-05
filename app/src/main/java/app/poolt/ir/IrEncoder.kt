package app.poolt.ir

object IrEncoder {
    fun encode(profile: IrProfile, command: Int): IntArray = when (profile.protocol) {
        IrProtocolType.NEC -> nec(profile.address and 0xFF, command and 0xFF)
        IrProtocolType.SAMSUNG32 -> samsung32(profile.address and 0xFFFF, command and 0xFF)
        IrProtocolType.RAW -> error("RAW patterns are not encoded from integer commands")
    }

    private fun nec(address: Int, command: Int): IntArray {
        val bytes = intArrayOf(
            address and 0xFF,
            address.inv() and 0xFF,
            command and 0xFF,
            command.inv() and 0xFF
        )
        return pulseDistance(
            headerMark = 9000,
            headerSpace = 4500,
            bitMark = 560,
            zeroSpace = 560,
            oneSpace = 1690,
            bytes = bytes
        )
    }

    private fun samsung32(address: Int, command: Int): IntArray {
        val bytes = intArrayOf(
            address and 0xFF,
            (address shr 8) and 0xFF,
            command and 0xFF,
            command.inv() and 0xFF
        )
        return pulseDistance(
            headerMark = 4500,
            headerSpace = 4500,
            bitMark = 560,
            zeroSpace = 560,
            oneSpace = 1690,
            bytes = bytes
        )
    }

    private fun pulseDistance(
        headerMark: Int,
        headerSpace: Int,
        bitMark: Int,
        zeroSpace: Int,
        oneSpace: Int,
        bytes: IntArray
    ): IntArray {
        val out = ArrayList<Int>(2 + bytes.size * 16 + 1)
        out += headerMark
        out += headerSpace
        for (byte in bytes) {
            for (bit in 0 until 8) {
                out += bitMark
                out += if (((byte shr bit) and 1) == 1) oneSpace else zeroSpace
            }
        }
        out += bitMark
        return out.toIntArray()
    }
}
