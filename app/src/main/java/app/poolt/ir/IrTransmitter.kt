package app.poolt.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import app.poolt.core.RemoteCommand

class IrTransmitter(context: Context) {
    private val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean
        get() = manager?.hasIrEmitter() == true

    fun carrierRanges(): List<IntRange> =
        manager?.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty()

    fun send(profile: IrProfile, command: RemoteCommand): Result<Unit> = runCatching {
        val ir = manager ?: error("ИК-модуль недоступен")
        if (!ir.hasIrEmitter()) error("На устройстве не найден ИК-передатчик")
        val code = profile.commands[command] ?: error("Для этой кнопки нет ИК-кода в выбранном профиле")
        val pattern = IrEncoder.encode(profile, code)
        ir.transmit(profile.frequency, pattern)
    }
}
