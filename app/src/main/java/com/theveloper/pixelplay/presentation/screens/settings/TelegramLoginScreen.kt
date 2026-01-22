package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.presentation.viewmodel.TelegramLoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginScreen(
    onBackPressed: () -> Unit,
    viewModel: TelegramLoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Login") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (uiState.step) {
                        TelegramLoginViewModel.LoginStep.PHONE -> {
                            OutlinedTextField(
                                value = uiState.phoneNumber,
                                onValueChange = viewModel::onPhoneNumberChanged,
                                label = { Text("Phone Number (with country code)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = viewModel::sendPhoneNumber,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Send Code")
                            }
                        }
                        TelegramLoginViewModel.LoginStep.CODE -> {
                            OutlinedTextField(
                                value = uiState.code,
                                onValueChange = viewModel::onCodeChanged,
                                label = { Text("Authentication Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = viewModel::checkCode,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify Code")
                            }
                        }
                        TelegramLoginViewModel.LoginStep.PASSWORD -> {
                            OutlinedTextField(
                                value = uiState.password,
                                onValueChange = viewModel::onPasswordChanged,
                                label = { Text("2FA Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = viewModel::checkPassword,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Login")
                            }
                        }
                        TelegramLoginViewModel.LoginStep.LOGGED_IN -> {
                            Text("Logged in as ${uiState.phoneNumber}")
                            Button(
                                onClick = { /* TODO: Logout */ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Logout")
                            }
                        }
                    }
                    
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
