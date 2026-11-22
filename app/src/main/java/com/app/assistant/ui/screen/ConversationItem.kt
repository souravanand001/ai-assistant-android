package com.app.assistant.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.app.assistant.R
import com.app.assistant.model.Conversation
import com.app.assistant.model.Attachment
import com.app.assistant.util.Category
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.app.assistant.ui.theme.AssistantTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConversationItem(
    conversation: Conversation,
    index: Int,
    isSelected: Boolean,
    onLongClick: (Int) -> Unit,
    isTranslateEnabled: Boolean,
    backgroundColor: Color =
        if (conversation.isMe) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    cornerRadius: Dp = 20.dp,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            horizontalArrangement = if (conversation.isMe) Arrangement.Start else Arrangement.End,
        ) {
            if (conversation.isMe) {
                Spacer(modifier = Modifier.weight(1f))
            }
            Card(
                Modifier
                    .wrapContentWidth()
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.85f),
                shape = RoundedCornerShape(cornerRadius),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                backgroundColor
                            },
                    ),
            ) {
                if (conversation.isLoading) {
                    LoadingDots() // Show loading animation when isLoading is true
                } else {
                    if (conversation.isMe) {
                        OtherCard(conversation, onLongClick, index, cornerRadius)
                    } else {
                        when (conversation.category) {
                            Category.CALL.name -> {
                                MakeCall(uriHandler, conversation)
                            }

                            Category.OTHER.name -> {
                                OtherCard(conversation, onLongClick, index, cornerRadius)
                            }

                            Category.SETTINGS.name -> {
                                // TODO
                                OtherCard(conversation, onLongClick, index, cornerRadius)
                            }

                            Category.SONGS.name -> {
                                PlaySong(uriHandler, conversation)
                            }

                            Category.NAVIGATION.name -> {
                                StartNavigation(uriHandler, conversation)
                            }

                            Category.WEATHER.name -> {
                                ShowWeather(uriHandler, conversation)
                            }

                            Category.REMINDER.name -> {
                                OtherCard(conversation, onLongClick, index, cornerRadius)
                            }

                            Category.ALARM.name -> {
                                OtherCard(conversation, onLongClick, index, cornerRadius)
                            }

                            else -> {
                                OtherCard(conversation, onLongClick, index, cornerRadius)
                            }
                        }
                    }
                }
            }
            if (!conversation.isMe) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (conversation.isMe) {
            Text(
                text = conversation.category,
                modifier =
                    Modifier
                        .padding(0.dp, 0.dp, 20.dp, 0.dp)
                        .fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.End,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StartNavigation(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    Row(
        modifier =
            Modifier
                .padding(8.dp)
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ),
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = Icons.Default.LocationOn,
                contentDescription = stringResource(id = R.string.navigation_desc),
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically),
            markdown = conversation.text,
        )
    }
}

@Composable
private fun PlaySong(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    AsyncImage(
        modifier =
            Modifier
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        model = conversation.contentURL,
        contentDescription = conversation.text,
    )
}

@Composable
private fun MakeCall(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    Row(
        modifier =
            Modifier
                .padding(8.dp)
                .clickable {
                    uriHandler.openUri(conversation.navigationURI.toString())
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ),
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = Icons.Default.Call,
                contentDescription = stringResource(id = R.string.call_desc),
            )
        }

        MarkdownText(
            modifier =
                Modifier
                    .align(alignment = Alignment.CenterVertically),
            markdown = conversation.text,
        )
    }
}

@Composable
private fun ShowWeather(
    uriHandler: UriHandler,
    conversation: Conversation,
) {
    MarkdownText(
        modifier =
            Modifier
                .padding(16.dp)
                .clickable { uriHandler.openUri(conversation.navigationURI.toString()) },
        markdown = conversation.text,
    )
}

@Composable
private fun OtherCard(
    conversation: Conversation,
    onLongClick: (Int) -> Unit,
    index: Int,
    cornerRadius: Dp = 20.dp,
) {
    val thinkingText = conversation.getThinkingProcess()
    val answerText = conversation.getActualAnswer()
    val hasText = answerText.isNotEmpty() || conversation.isStreaming || thinkingText != null

    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        onLongClick(index)
                    },
                )
            }
    ) {
        if (conversation.attachments.isNotEmpty()) {
            ConversationAttachments(
                attachments = conversation.attachments,
                hasTextBelow = hasText,
                cornerRadius = cornerRadius,
                modifier = Modifier
            )
        }

        if (thinkingText != null || answerText.isNotEmpty() || conversation.isStreaming) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (conversation.attachments.isNotEmpty()) 8.dp else 8.dp,
                    bottom = 8.dp
                )
            ) {
                if (thinkingText != null) {
                    var isExpanded by remember { mutableStateOf(true) }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧠",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Thinking Process",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = thinkingText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (answerText.isNotEmpty()) {
                    val displayAnswer = if (conversation.isStreaming) {
                        "$answerText ▮"
                    } else {
                        answerText
                    }

                    MarkdownText(
                        markdown = displayAnswer,
                        modifier = Modifier
                    )
                } else if (conversation.isStreaming && thinkingText == null) {
                    LoadingDots()
                }
            }
        }
    }
}

@Composable
fun ConversationAttachments(
    attachments: List<Attachment>,
    hasTextBelow: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEachIndexed { index, attachment ->
            val isFirst = index == 0
            val isLast = index == attachments.size - 1
            when {
                attachment.mimeType.startsWith("image/") -> {
                    ImageAttachmentBubble(
                        attachment = attachment,
                        isFirst = isFirst,
                        isLast = isLast,
                        hasTextBelow = hasTextBelow,
                        cornerRadius = cornerRadius
                    )
                }
                attachment.mimeType.startsWith("audio/") -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AudioAttachmentBubble(attachment)
                    }
                }
                attachment.mimeType.startsWith("video/") -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        VideoAttachmentBubble(attachment)
                    }
                }
                else -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        DocumentAttachmentBubble(attachment)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageAttachmentBubble(
    attachment: Attachment,
    isFirst: Boolean,
    isLast: Boolean,
    hasTextBelow: Boolean,
    cornerRadius: Dp
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var imageLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(attachment) {
        isLoading = true
        imageLoaded = false
        withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(attachment.filePath)
                if (file.exists()) {
                    val fileBytes = file.readBytes()
                    val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                    bitmap = decoded
                    if (decoded != null) {
                        imageLoaded = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    val topRounding = if (isFirst) cornerRadius else 0.dp
    val bottomRounding = if (isLast && !hasTextBelow) cornerRadius else 0.dp
    val imageShape = RoundedCornerShape(
        topStart = topRounding,
        topEnd = topRounding,
        bottomStart = bottomRounding,
        bottomEnd = bottomRounding
    )

    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "imageFadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(imageShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val fileBytes = java.io.File(attachment.filePath).readBytes()
                        val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                        val ext = attachment.fileName.substringAfterLast('.', "jpg")
                        val temp = java.io.File(context.cacheDir, "view_${attachment.id}.$ext")
                        temp.writeBytes(decrypted)
                        
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "com.app.assistant.fileprovider",
                            temp
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Cannot open image: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentScale = ContentScale.Crop,
                alpha = imageAlpha
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load image",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AudioAttachmentBubble(attachment: Attachment) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0) }
    val mediaPlayer = remember { android.media.MediaPlayer() }
    var tempFile by remember { mutableStateOf<java.io.File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {}
            tempFile?.delete()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                try {
                    currentPosition = mediaPlayer.currentPosition
                    progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(200)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    if (tempFile == null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val fileBytes = java.io.File(attachment.filePath).readBytes()
                                val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                                val temp = java.io.File(context.cacheDir, "play_${attachment.id}.mp3")
                                temp.writeBytes(decrypted)
                                tempFile = temp

                                withContext(Dispatchers.Main) {
                                    mediaPlayer.reset()
                                    mediaPlayer.setDataSource(temp.absolutePath)
                                    mediaPlayer.prepare()
                                    duration = mediaPlayer.duration
                                    mediaPlayer.start()
                                    isPlaying = true
                                    mediaPlayer.setOnCompletionListener {
                                        isPlaying = false
                                        progress = 0f
                                        currentPosition = 0
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        mediaPlayer.start()
                        isPlaying = true
                    }
                }
            }) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun VideoAttachmentBubble(attachment: Attachment) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileBytes = java.io.File(attachment.filePath).readBytes()
                    val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                    val ext = attachment.fileName.substringAfterLast('.', "mp4")
                    val temp = java.io.File(context.cacheDir, "play_${attachment.id}.$ext")
                    temp.writeBytes(decrypted)
                    
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "com.app.assistant.fileprovider",
                        temp
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Cannot play video: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_stop),
                contentDescription = "Play Video",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to play video",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DocumentAttachmentBubble(attachment: Attachment) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileBytes = java.io.File(attachment.filePath).readBytes()
                    val decrypted = com.app.assistant.db.EncryptionUtil.decryptFile(fileBytes, attachment.iv)
                    val ext = attachment.fileName.substringAfterLast('.', "pdf")
                    val temp = java.io.File(context.cacheDir, "doc_${attachment.id}.$ext")
                    temp.writeBytes(decrypted)
                    
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "com.app.assistant.fileprovider",
                        temp
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, attachment.mimeType)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Cannot open document: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_translate),
                contentDescription = "Open Document",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subText = if (attachment.mimeType.startsWith("text/") || attachment.mimeType == "application/json") {
                    "Text appended inline (Tap to open)"
                } else {
                    "Tap to open document"
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Hello! Can you help me?",
                    isMe = true,
                    category = Category.OTHER.name
                ),
                index = 0,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AiConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Sure! I can help you set an alarm or make a call.",
                    isMe = false,
                    category = Category.OTHER.name
                ),
                index = 1,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "",
                    isMe = false,
                    isLoading = true
                ),
                index = 2,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherConversationItemPreview() {
    AssistantTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ConversationItem(
                conversation = Conversation(
                    text = "Today's weather is sunny, 25°C.",
                    isMe = false,
                    category = Category.WEATHER.name
                ),
                index = 3,
                isSelected = false,
                onLongClick = {},
                isTranslateEnabled = false
            )
        }
    }
}
