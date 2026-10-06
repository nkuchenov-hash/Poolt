package app.poolt.ir

object IrEncoder {
    fun encode(profile: IrProfile, command: Int): IntArray = when (profile.protocol) {
        IrProtocolType.NEC -> nec(profile.address and 0xFF, command and 0xFF)
        IrProtocolType.NEC_EXT -> necExtended(profile.address and 0xFF, profile.subAddress and 0xFF, command and 0xFF)
        IrProtocolType.SAMSUNG32 -> samsung32(profile.address and 0xFFFF, command and 0xFF)
        IrProtocolType.SONY12 -> sony(command and 0x7F, profile.address and 0x1F, profile.subAddress, 12)
        IrProtocolType.SONY15 -> sony(command and 0x7F, profile.address and 0xFF, profile.subAddress, 15)
        IrProtocolType.SONY20 -> sony(command and 0x7F, profile.address and 0x1F, profile.subAddress and 0xFF, 20)
        IrProtocolType.RAW -> error("RAW patterns are not encoded from integer commands")
    }

    private fun nec(address: Int, command: Int): IntArray =
        pulseDistance(9000, 4500, 560, 560, 1690, intArrayOf(address, address.inv() and 0xFF, command, command.inv() and 0xFF))

    private fun necExtended(address: Int, subAddress: Int, command: Int): IntArray =
        pulseDistance(9000, 4500, 560, 560, 1690, intArrayOf(address, subAddress, command, command.inv() and 0xFF))

    private fun samsung32(address: Int, command: Int): IntArray =
        pulseDistance(4500, 4500, 560, 560, 1690, intArrayOf(address and 0xFF, (address shr 8) and 0xFF, command, command.inv() and 0xFF))

    private fun sony(command: Int, device: Int, subDevice: Int, bits: Int): IntArray {
        var data = command.toLong() and 0x7F
        data = data or ((device.toLong() and if (bits == 15) 0xFF else 0x1F) shl 7)
        if (bits == 20) data = data or ((subDevice.toLong() and 0xFF) shl 12)
        val out = ArrayList<Int>(2 + bits * 2)
        out += 2400
        out += 600
        for (bit in 0 until bits) {
            out += if (((data shr bit) and 1L) == 1L) 1200 else 600
            out += 600
        }
        return out.toIntArray()
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
