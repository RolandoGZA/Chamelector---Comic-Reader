package com.rolandb.chamelector.ui.theme.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicExplorerApp(viewModel: ComicViewModel) {
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val comics by viewModel.comics.collectAsState()

    // Contract para el explorador SAF
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedFolderUri == null) "Lector de Cómics" else "Mi Biblioteca") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (selectedFolderUri == null) {
                // Estado 1: Solicitar selección de carpeta
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Selecciona la carpeta donde guardas tus cómics (.cbr)")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Seleccionar Carpeta")
                    }
                }
            } else {
                // Estado 2: Dashboard con las miniaturas
                if (comics.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(comics) { comic ->
                            ComicCard(comic = comic)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComicCard(comic: ComicItem) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(220.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            if (comic.cover != null) {
                Image(
                    bitmap = comic.cover.asImageBitmap(),
                    contentDescription = comic.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error al leer RAR\n(Comprueba en Logcat)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Text(
                text = comic.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                maxLines = 1
            )
        }
    }
}