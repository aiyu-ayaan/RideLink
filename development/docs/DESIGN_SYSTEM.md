# Design system

Material 3 Expressive, Compose only. Matches Findex and domus.

## Theme
- `MaterialExpressiveTheme` with `MotionScheme.expressive()`, default `Shapes()`, default `Typography()`
  (Roboto Flex, including the Emphasized roles).
- Dynamic color on Android 12+. Fallback: tonal-spot scheme from seed `#B3261E` (scooter red),
  light and dark, in `ui/theme/Theme.kt`.
- Status/nav bar icon colors follow the in-app theme (SideEffect in `RideLinkTheme`).

## Color roles
| Surface | Container | Content |
|---|---|---|
| Hero card, connected | `primaryContainer` | `onPrimaryContainer`; badge `primary`/`onPrimary` |
| Hero card, not connected | `surfaceContainerHigh` | `onSurface`; badge `secondaryContainer` |
| Ride dashboard | `tertiaryContainer` | `onTertiaryContainer`; arrow badge `onTertiaryContainer` bg with `tertiaryContainer` icon; stat tiles `onTertiaryContainer` at 8% |
| Call card | `secondaryContainer` | `onSecondaryContainer` |
| Permission prompts | `errorContainer` | `onErrorContainer` |

## Shape
- Status badge: `MaterialShapes.Cookie9Sided`. Turn arrow badge: `MaterialShapes.Cookie7Sided`.
- Cards: `shapes.extraLarge` (hero, dashboard), `shapes.large` (info, permissions, stat tiles).
- Buttons: default full shape, min height 56dp for the primary ride action.
- Button groups: connected `ToggleButton`s with `ButtonGroupDefaults.connected*ButtonShapes()`.

## Type
- Hero title `headlineSmallEmphasized`; turn distance `displayMediumEmphasized`;
  stat values `headlineSmallEmphasized`; labels `labelMedium`.

## Spacing
`Dimens`: 4 / 8 / 12 / 16 / 24 / 32dp, gutter 16dp, min touch 48dp, content max width 640dp.

## Motion
- Tab switch: crossfade with `motionScheme.defaultEffectsSpec`.
- Turn arrow change: scale + fade with `motionScheme.defaultSpatialSpec`.
- Cards appear with `expandVertically + fadeIn`.
- Waiting for the scooter: expressive `LoadingIndicator`, never a plain spinner.

## Accessibility
- Hero, dashboard and call cards are polite live regions (status changes are announced).
- Decorative icons have `contentDescription = null`; console status icons are labelled.
- Rows with switches/radios are whole-row `toggleable`/`selectable` with roles.
