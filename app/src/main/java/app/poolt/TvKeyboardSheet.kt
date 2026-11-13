package app.poolt

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvKeyboardSheet(
    state: MainUiState,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val tvReady = state.mode == ControlMode.WIFI &&
        state.selected?.isOnline == true &&
        state.selected?.driverId in setOf("lg.webos", "samsung.smarttv")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF171B23)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        ) {
            Text("Клавиатура телевизора", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                if (tvReady) "Выбери поле ввода на ТВ и набери текст здесь."
                else "Для ввода подключи LG или Samsung по Wi-Fi.",
                fontSize = 13.sp,
                color = androidx.compose.ui.graphics.Color(0xFFACB1BF)
            )
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                label = { Text("Текст для телевизора") },
                minLines = 1,
                maxLines = 4,
                enabled = tvReady,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (tvReady) vm.sendTvText(input) })
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.sendTvText(input) },
                    enabled = tvReady && input.isNotBlank() && !state.keyboardSending,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(if (state.keyboardSending) "Отправка…" else "Ввести на ТВ")
                }
                FilledTonalIconButton(
                    onClick = { vm.tvKeyboardAction("backspace") },
                    enabled = tvReady,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Backspace, contentDescription = "Удалить символ на ТВ")
                }
                FilledTonalIconButton(
                    onClick = { vm.tvKeyboardAction("enter") },
                    enabled = tvReady,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.KeyboardReturn, contentDescription = "Enter на ТВ")
                }
            }
            if (state.keyboardMessage.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(state.keyboardMessage, fontSize = 13.sp)
            }
            Spacer(Modifier.height(5.dp))
        }
    }
    LaunchedEffect(tvReady) {
        if (tvReady) {
            requester.requestFocus()
            keyboard?.show()
        }
    }
}
