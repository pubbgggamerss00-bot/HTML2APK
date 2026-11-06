package ir.html2apk.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.html2apk.app.ui.theme.Container
import ir.html2apk.app.ui.theme.ContainerLow
import ir.html2apk.app.ui.theme.ErrorContainerNavy
import ir.html2apk.app.ui.theme.ErrorOnContainerNavy
import ir.html2apk.app.ui.theme.ErrorRed
import ir.html2apk.app.ui.theme.OnSurfaceVariantNavy
import ir.html2apk.app.ui.theme.OutlineVariant
import ir.html2apk.app.ui.theme.Primary
import ir.html2apk.app.ui.theme.TextWhite
import ir.html2apk.app.ui.theme.Warning

/** Navy card container with hairline border — the original card look. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Container.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

/** Collapsible section (signing settings / manifest options). */
@Composable
fun CollapsibleSection(
    title: String,
    subtitle: String? = null,
    initialExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "بستن" else "بازکردن",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(content = content)
        }
    }
}

/** Red error banner with a dismiss area. */
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ErrorContainerNavy,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "خطا",
                style = MaterialTheme.typography.labelLarge,
                color = ErrorRed,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorOnContainerNavy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * OutlinedTextField with EVERY color pinned explicitly.
 *
 * Why not the stock defaults: the theme's resolved input-text color on some
 * devices / Compose stacks came out unreadable against the dark surfaces —
 * the user typed text and literally could not see what was written.
 * Here the typed text is always bright (TextWhite on ContainerLow, exactly
 * like the original TechField), the cursor is always emerald and the textStyle
 * color is set directly as a second guarantee, so visibility can never regress
 * with theme/IME quirks.
 */
@Composable
fun H2ATextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(color = TextWhite),
        label = if (label != null) {
            { Text(label) }
        } else null,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite,
            disabledTextColor = OnSurfaceVariantNavy,
            errorTextColor = TextWhite,
            focusedContainerColor = ContainerLow,
            unfocusedContainerColor = ContainerLow,
            disabledContainerColor = ContainerLow,
            errorContainerColor = ContainerLow,
            cursorColor = Primary,
            errorCursorColor = ErrorRed,
            focusedBorderColor = Primary,
            unfocusedBorderColor = OutlineVariant.copy(alpha = 0.8f),
            errorBorderColor = ErrorRed.copy(alpha = 0.9f),
            focusedLabelColor = Primary,
            unfocusedLabelColor = OnSurfaceVariantNavy,
            errorLabelColor = ErrorRed,
        ),
    )
}

/** Small amber inline note (original Warning color). */
@Composable
fun InfoNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Warning,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
