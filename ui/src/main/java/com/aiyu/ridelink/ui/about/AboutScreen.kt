package com.aiyu.ridelink.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import com.aiyu.ridelink.ui.nav.appBarColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import com.aiyu.ridelink.core.profile.GitHubProfile
import com.aiyu.ridelink.core.profile.GitHubUser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.core.update.Releases
import com.aiyu.ridelink.ui.R
import com.aiyu.ridelink.ui.nav.LinkRow
import com.aiyu.ridelink.ui.theme.Dimens
import com.aiyu.ridelink.ui.update.BackButton

private const val DEVELOPER = "aiyu-ayaan"
private const val GITHUB_URL = "https://github.com/aiyu-ayaan"
private const val PORTFOLIO_URL = "https://aiyu.co.in"
private const val SOURCE_URL = "https://github.com/${Releases.REPOSITORY}"

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUpdates: () -> Unit, onOpenAcknowledgements: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val info = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Live from GitHub; the placeholder below shows while offline or rate limited.
    val user by produceState<GitHubUser?>(null) { value = GitHubProfile.fetch(DEVELOPER) }
    val avatar by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, user?.avatarUrl) {
        value = user?.avatarUrl?.let { GitHubProfile.avatar(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.about_title)) },
                subtitle = { Text(stringResource(R.string.about_tagline)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            SectionTitle(R.string.about_app)
            Group {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.about_version, info?.versionName ?: "?", info?.longVersionCode ?: 0L)) },
                    leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    colors = itemColors(),
                )
                LinkRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_title), stringResource(R.string.about_updates_desc), onClick = onOpenUpdates)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_privacy)) },
                    supportingContent = { Text(stringResource(R.string.about_privacy_desc)) },
                    leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    colors = itemColors(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_disclaimer)) },
                    supportingContent = { Text(stringResource(R.string.about_disclaimer_desc)) },
                    leadingContent = { Icon(Icons.Rounded.Warning, contentDescription = null) },
                    colors = itemColors(),
                )
            }

            SectionTitle(R.string.about_developer)
            Group {
                Column(Modifier.fillMaxWidth().padding(Dimens.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Box(
                        Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        val picture = avatar
                        if (picture != null) {
                            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text("A", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(user?.name ?: DEVELOPER, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Dimens.sm))
                    if (user?.name != null) Text("@$DEVELOPER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(user?.bio ?: stringResource(R.string.about_built), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    user?.let { u ->
                        Text(
                            listOfNotNull(
                                u.location,
                                stringResource(R.string.about_followers, u.followers),
                                stringResource(R.string.about_repos, u.publicRepos),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                LinkRow(Icons.Rounded.Code, stringResource(R.string.about_github), GITHUB_URL.removePrefix("https://"), external = true) { open(GITHUB_URL) }
                LinkRow(Icons.Rounded.Language, stringResource(R.string.about_portfolio), PORTFOLIO_URL.removePrefix("https://"), external = true) { open(PORTFOLIO_URL) }
            }

            SectionTitle(R.string.about_open_source)
            Group {
                LinkRow(Icons.Rounded.Code, stringResource(R.string.about_source), SOURCE_URL.removePrefix("https://"), external = true) { open(SOURCE_URL) }
                LinkRow(Icons.Rounded.Description, stringResource(R.string.about_acknowledgements), stringResource(R.string.about_acknowledgements_desc), onClick = onOpenAcknowledgements)
            }
        }
    }
}

/** Name, project URL, license. Keep in sync with gradle/libs.versions.toml. */
private data class Library(val name: String, val url: String, val license: String)

private const val APACHE = "Apache License 2.0"

private val libraries = listOf(
    Library("Kotlin", "https://github.com/JetBrains/kotlin", APACHE),
    Library("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines", APACHE),
    Library("Jetpack Compose", "https://developer.android.com/jetpack/compose", APACHE),
    Library("Material 3 for Compose", "https://developer.android.com/jetpack/androidx/releases/compose-material3", APACHE),
    Library("Material Icons Extended", "https://developer.android.com/jetpack/androidx/releases/compose-material", APACHE),
    Library("AndroidX Core KTX", "https://developer.android.com/jetpack/androidx/releases/core", APACHE),
    Library("AndroidX Core SplashScreen", "https://developer.android.com/jetpack/androidx/releases/core", APACHE),
    Library("AndroidX Lifecycle", "https://developer.android.com/jetpack/androidx/releases/lifecycle", APACHE),
    Library("AndroidX Activity Compose", "https://developer.android.com/jetpack/androidx/releases/activity", APACHE),
    Library("AndroidX DataStore", "https://developer.android.com/jetpack/androidx/releases/datastore", APACHE),
    Library("Hilt (Dagger)", "https://github.com/google/dagger", APACHE),
    Library("Open-Meteo weather and air quality data", "https://open-meteo.com", "CC BY 4.0"),
)

@Composable
fun AcknowledgementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                colors = appBarColors(),
                title = { Text(stringResource(R.string.about_acknowledgements)) },
                subtitle = { Text(stringResource(R.string.about_acknowledgements_subtitle)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter).padding(bottom = Dimens.xl)) {
            Group {
                libraries.forEach { lib ->
                    LinkRow(null, lib.name, "${lib.license}\n${lib.url.removePrefix("https://")}", external = true) { open(lib.url) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.xs, top = Dimens.lg, bottom = Dimens.xs),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column { content() }
    }
}

@Composable
private fun itemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
