package com.codewithram.secretchat.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp

@Composable
fun GroupInfoHeader(
    avatarBitmap: Bitmap,
    groupName: String,
    onEditClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        // Curved stripe background
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            val path = Path().apply {
                moveTo(0f, height * 0.4f)
                cubicTo(
                    width * 0.25f, 0f,
                    width * 0.75f, 0f,
                    width, height * 0.4f
                )
                lineTo(width, 0f)
                lineTo(0f, 0f)
                close()
            }

            drawPath(path, color = Color(0xFF9C27B0)) // purple_500
        }

        // Avatar and Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 16.dp, top = 40.dp)
        ) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = groupName,
                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GroupInfoHeaderPreview() {
    // Create a simple placeholder Bitmap (e.g., 1x1 pixel)
    val width = 100
    val height = 100
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.MAGENTA) // Fill with magenta for visibility

    GroupInfoHeader(
        avatarBitmap = bitmap,
        groupName = "Secret Hackers",
        onEditClick = {} // no-op for preview
    )
}
