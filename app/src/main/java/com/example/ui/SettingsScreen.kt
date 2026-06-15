package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()

    var inputUsername by remember(username) { mutableStateOf(username ?: "") }
    var inputToken by remember(token) { mutableStateOf(token ?: "") }

    val isConnected = !username.isNullOrBlank() && !token.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Connected as: \$username",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Not Connected. Enter GitHub credentials to sync repos.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedTextField(
                value = inputUsername,
                onValueChange = { inputUsername = it },
                label = { Text("GitHub Username") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = inputToken,
                onValueChange = { inputToken = it },
                label = { Text("Personal Access Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("token_input"),
                singleLine = true
            )

            Button(
                onClick = { viewModel.saveCredentials(inputUsername, inputToken) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_button")
            ) {
                Text(if (isConnected) "Update Credentials" else "Connect")
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = { viewModel.clearCredentials() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disconnect_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}
