package com.williamfq.xhat.ui.screens.auth

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.williamfq.xhat.ui.Navigation.Screen
import com.williamfq.xhat.ui.components.LocationPermissionDialog
import com.williamfq.xhat.ui.components.ProfileDatePicker
import com.williamfq.xhat.ui.profile.ProfileSetupState
import com.williamfq.xhat.ui.profile.ProfileSetupViewModel
import com.williamfq.xhat.ui.profile.ProfileState
import com.williamfq.xhat.ui.theme.XhatTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Función auxiliar para calcular la edad a partir de la fecha de nacimiento (formato "dd/MM/yyyy")
fun calculateAge(birthDate: String): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val date = LocalDate.parse(birthDate, formatter)
        val now = LocalDate.now()
        val years = ChronoUnit.YEARS.between(date, now)
        years.toString()
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun ProfileSetupScreen(
    navController: NavController,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()

    // Observar cambios en el estado para navegación.
    // Al detectar éxito, navega y resetea el estado para evitar múltiples ejecuciones.
    LaunchedEffect(key1 = uiState) {
        if (uiState is ProfileSetupState.Success) {
            navController.navigate(Screen.Main.route) {
                popUpTo(Screen.PhoneNumber.route) { inclusive = true }
            }
            viewModel.resetUiState()
        }
    }

    XhatTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
            ) {
                ProfileHeader(
                    profileImageUri = profileState.profileImageUri,
                    coverImageUri = profileState.coverImageUri,
                    onProfileImageClick = { viewModel.selectProfileImage() },
                    onCoverImageClick = { viewModel.selectCoverImage() }
                )
                ProfileForm(
                    profileState = profileState,
                    onUsernameChange = viewModel::updateUsername,
                    onNameChange = viewModel::updateName,
                    onDescriptionChange = viewModel::updateDescription,
                    onBirthDateChange = viewModel::updateBirthDate,
                    onLocationClick = viewModel::requestLocation
                )
                // Campo de Edad, calculada a partir de la fecha de nacimiento
                AgeField(birthDate = profileState.birthDate)
                SaveButton(
                    isLoading = uiState is ProfileSetupState.Loading,
                    isEnabled = viewModel.isProfileValid(),
                    onClick = {
                        viewModel.updateProfile(
                            name = profileState.name,
                            description = profileState.description,
                            country = profileState.country,
                            state = profileState.state,
                            city = profileState.city,
                            birthDate = profileState.birthDate
                        )
                        viewModel.saveProfile()
                    }
                )
                if (uiState is ProfileSetupState.Error) {
                    ErrorMessage(message = (uiState as ProfileSetupState.Error).message)
                }
            }
        }
    }

    if (profileState.showDatePicker) {
        ProfileDatePicker(
            onDateSelected = viewModel::updateBirthDate,
            onDismiss = { viewModel.toggleDatePicker(false) }
        )
    }

    if (profileState.showLocationPermission) {
        LocationPermissionDialog(
            onAllow = {
                // Aquí se debe implementar la solicitud del permiso de ubicación utilizando,
                // por ejemplo, ActivityResultLauncher o Accompanist Permissions.
                viewModel.requestLocationPermission()
            },
            onDeny = { viewModel.toggleLocationPermission(false) }
        )
    }
}

@Composable
private fun ProfileHeader(
    profileImageUri: Uri?,
    coverImageUri: Uri?,
    onProfileImageClick: () -> Unit,
    onCoverImageClick: () -> Unit
) {
    // Encabezado con portada y avatar; el avatar se posiciona para superponerse a la portada
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Portada (cover image)
        if (coverImageUri != null) {
            val coverPainter = rememberAsyncImagePainter(model = coverImageUri)
            Image(
                painter = coverPainter,
                contentDescription = "Cover Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { onCoverImageClick() },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onCoverImageClick() }
            )
        }
        // Botón para cambiar la portada (esquina superior derecha)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            IconButton(onClick = onCoverImageClick) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Cambiar Portada",
                    tint = Color.White
                )
            }
        }
        // Avatar: foto de perfil circular (más grande) con fondo de color y borde, posicionado para superponerse a la portada
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 5.dp)
                .clip(CircleShape)
                .background(Color.LightGray, CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .clickable { onProfileImageClick() }
        ) {
            if (profileImageUri != null) {
                val profilePainter = rememberAsyncImagePainter(model = profileImageUri)
                Image(
                    painter = profilePainter,
                    contentDescription = "Profile Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Default Avatar",
                    tint = Color.Gray,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Botón para cambiar el avatar (pequeño, en la esquina inferior derecha del avatar)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
            ) {
                IconButton(onClick = onProfileImageClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Cambiar Avatar",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    profileState: ProfileState,
    onUsernameChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onBirthDateChange: (Long) -> Unit,
    onLocationClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        UsernameField(
            username = profileState.username,
            error = profileState.usernameError,
            onUsernameChange = onUsernameChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        NameField(
            name = profileState.name,
            error = null,
            onNameChange = onNameChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        DescriptionField(
            description = profileState.description,
            error = null,
            onDescriptionChange = onDescriptionChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        LocationFields(
            country = profileState.country,
            state = profileState.state,
            city = profileState.city,
            onLocationClick = onLocationClick
        )
        Spacer(modifier = Modifier.height(8.dp))
        BirthDateField(
            birthDate = profileState.birthDate,
            error = null,
            onClick = { onBirthDateChange(System.currentTimeMillis()) }
        )
    }
}

@Composable
private fun AgeField(birthDate: String) {
    // Calcula la edad a partir de la fecha de nacimiento y la muestra en un campo de solo lectura.
    val age = if (birthDate.isNotBlank()) calculateAge(birthDate) else ""
    OutlinedTextField(
        value = age,
        onValueChange = {},
        label = { Text("Edad") },
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        enabled = isEnabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("Guardar perfil")
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun UsernameField(
    username: String,
    error: String?,
    onUsernameChange: (String) -> Unit
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Nombre de usuario") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NameField(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Nombre") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DescriptionField(
    description: String,
    error: String?,
    onDescriptionChange: (String) -> Unit
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Descripción") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 5
    )
}

@Composable
private fun LocationFields(
    country: String,
    state: String,
    city: String,
    onLocationClick: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = country,
            onValueChange = {},
            label = { Text("País") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state,
            onValueChange = {},
            label = { Text("Estado/Provincia") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = city,
            onValueChange = {},
            label = { Text("Ciudad") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = onLocationClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualizar ubicación")
        }
    }
}

@Composable
private fun BirthDateField(
    birthDate: String,
    error: String?,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = birthDate,
        onValueChange = {},
        label = { Text("Fecha de nacimiento") },
        readOnly = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileSetupScreenPreview() {
    // Valores de ejemplo para previsualización
    val dummyProfileState = ProfileState(
        username = "usuario123",
        name = "Nombre Ejemplo",
        description = "Esta es una descripción de ejemplo.",
        country = "País Ejemplo",
        state = "Estado Ejemplo",
        city = "Ciudad Ejemplo",
        birthDate = "01/01/2000",
        profileImageUri = null,
        coverImageUri = null,
        showDatePicker = false,
        showLocationPermission = false,
        usernameError = null
    )
    ProfileSetupState.Idle

    XhatTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
            ) {
                ProfileHeader(
                    profileImageUri = dummyProfileState.profileImageUri,
                    coverImageUri = dummyProfileState.coverImageUri,
                    onProfileImageClick = {},
                    onCoverImageClick = {}
                )
                ProfileForm(
                    profileState = dummyProfileState,
                    onUsernameChange = {},
                    onNameChange = {},
                    onDescriptionChange = {},
                    onBirthDateChange = {},
                    onLocationClick = {}
                )
                AgeField(birthDate = dummyProfileState.birthDate)
                SaveButton(
                    isLoading = false,
                    isEnabled = true,
                    onClick = {}
                )
                ErrorMessage(message = "Error de ejemplo")
            }
        }
    }
}
