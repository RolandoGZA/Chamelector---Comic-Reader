package com.rolandb.chamelector.ui.theme.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.core.net.toUri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rolandb.chamelector.ui.theme.screens.comicviewer.ComicViewerScreen

@Composable
fun ComicExplorerApp(viewModel: ComicViewModel) {
    val navController = rememberNavController()
    val comics by viewModel.comics.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "explorer"
    ) {
        // Pantalla 1: Biblioteca y explorador de carpetas
        composable("explorer") {
            ComicLibraryScreen(
                viewModel = viewModel,
                onComicClick = { comic ->
                    val index = comics.indexOf(comic)
                    if (index != -1) {
                        navController.navigate("viewer/$index")
                    }
                }
            )
        }

        // Pantalla 2: Visor del cómic
        composable(
            route = "viewer/{comicIndex}",
            arguments = listOf(
                navArgument("comicIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("comicIndex") ?: -1
            val comic = comics.getOrNull(index)

            if (comic != null) {
                ComicViewerScreen(
                    comicUri = comic.uri,
                    comicTitle = comic.name,
                    onBackPress = { navController.popBackStack() }
                )
            } else {
                // Manejar error de índice
                Text("Error: Cómic no encontrado")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicLibraryScreen(
    viewModel: ComicViewModel,
    onComicClick: (ComicItem) -> Unit
) {
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
                    Text("Selecciona la carpeta donde guardas tus cómics (.cbr / .cbz)")
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
                            ComicCard(
                                comic = comic,
                                onClick = { onComicClick(comic) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComicCard(
    comic: ComicItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
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
                        text = "Sin vista previa",
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