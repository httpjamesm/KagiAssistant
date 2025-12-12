package space.httpjames.kagiassistantmaterial.ui.message

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    onDismissRequest: () -> Unit,
    onAttachment: (uri: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AttachCameraButton(
                onSnap = {
                    onAttachment(it)
                    onDismissRequest()
                }
            )
            AttachGalleryButton(
                onSelected = {
                    for (it in it) {
                        onAttachment(it.toString())
                    }
                    onDismissRequest()
                }
            )
            AttachFileButton(onSelected = { list -> list.forEach { onAttachment(it.toString()) }; onDismissRequest() })
        }
    }
}

@Composable
fun AttachCameraButton(
    onSnap: (photoUri: String) -> Unit = {}
) {
    val context = LocalContext.current
    val photoUri = remember { createTempImageUri(context) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
//            capturedBitmap.value = MediaStore.Images.Media.getBitmap(context.contentResolver, photoUri)
                onSnap(photoUri.toString())
            }
        }


    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = {
            launcher.launch(photoUri)
        }, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Camera"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Camera")
    }
}

private fun createTempImageUri(context: Context): Uri {
    // 1. create a real file in the cache directory
    val tempFile = File.createTempFile("temp_", ".webp", context.cacheDir)

    // 2. make it world-readable for the camera / gallery (optional)
    tempFile.createNewFile()

    // 3. get a content-Uri for that file through your FileProvider
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        tempFile
    )
}


@Composable
fun AttachGalleryButton(
    onSelected: (uris: List<Uri>) -> Unit = {}
) {
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        onSelected(uris)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = {
            pickMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = "Gallery"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Gallery")
    }
}

@Composable
fun AttachFileButton(onSelected: (List<Uri>) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> onSelected(uris) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = { launcher.launch(arrayOf("*/*")) },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = "Files")
        }
        Spacer(Modifier.height(12.dp))
        Text("Files")
    }
}
