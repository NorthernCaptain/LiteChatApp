package northern.captain.litechat.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import northern.captain.litechat.app.R

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.signInSuccess) {
        if (uiState.signInSuccess) {
            onSignInSuccess()
        }
    }

    if (uiState.isAutoLogging) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Image(
                painter = painterResource(R.drawable.chatwelcome),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.signing_in),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var showSignInConfirm by remember { mutableStateOf(false) }

    if (showSignInConfirm) {
        AlertDialog(
            onDismissRequest = { showSignInConfirm = false },
            title = { Text(stringResource(R.string.sign_in)) },
            text = { Text(stringResource(R.string.sign_in_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignInConfirm = false
                    viewModel.onGoToSignIn()
                }) {
                    Text(stringResource(R.string.sign_in))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignInConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.autoLoginFailed) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Image(
                painter = painterResource(R.drawable.chatwelcome),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.error_auto_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::onRetryAutoLogin,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.retry))
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = { showSignInConfirm = true },
            ) {
                Text(stringResource(R.string.sign_in))
            }
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(R.drawable.chatwelcome),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(28.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.onSignInClick()
                    }
                ),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onSignInClick,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.sign_in))
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn()
            ) {
                Text(
                    text = when (uiState.error) {
                        "empty_fields" -> stringResource(R.string.error_empty_fields)
                        "invalid_credentials" -> stringResource(R.string.error_invalid_credentials)
                        else -> stringResource(R.string.error_network)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
