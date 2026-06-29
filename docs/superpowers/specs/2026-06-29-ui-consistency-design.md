# Design Spec — Consistenza UI & Fix Shortcut App
**Data:** 2026-06-29

## Obiettivo

Rendere le 3 app (`simple-document-scanner`, `simple-shortcut-app`, `simple-transcription-app`) più coerenti tra loro adottando uno stile uniforme per la schermata Settings. In parallelo, correggere il flusso UX della selezione app in `simple-shortcut-app`.

---

## 1. Settings Screen — Stile unificato (tutte e 3 le app)

### Standard adottato: `ListItem` flat (Material 3)

Struttura comune per tutte le Settings:

- **Header di sezione**: `Text` con `style = MaterialTheme.typography.labelLarge`, colore `MaterialTheme.colorScheme.primary`, padding `horizontal = 16.dp, vertical = 12.dp`
- **Voci**: `ListItem` con `leadingContent` (icon), `headlineContent` (titolo), `supportingContent` opzionale (sottotitolo/valore corrente)
- **Separatori**: `HorizontalDivider()` tra sezioni
- **Scroll**: `Column` con `verticalScroll(rememberScrollState())`
- **TopAppBar**: `TopAppBar` standard (non `CenterAligned`) con titolo + back navigation

### Item versione (da aggiungere a document-scanner e transcription-app)

```
Sezione "About" / "Informazioni"
  [Icon.Info]  Versione
               1.1.0            ← BuildConfig.VERSION_NAME
```

Il `ListItem` versione non è cliccabile (nessun `onClick`).

### Modifiche per app

#### `simple-document-scanner`
- `app/build.gradle.kts`: aggiungere `buildConfig = true` in `buildFeatures`
- `SettingsScreen.kt`: riscrivere usando `ListItem` flat:
  - Sezione **Lingua** → `ListItem` cliccabile che apre il dialog esistente (invariato)
  - Sezione **Aggiornamenti** → `ListItem` "Cerca aggiornamenti" + `ListItem` "Changelog"  
  - Sezione **Informazioni** (nuova) → `ListItem` versione (non cliccabile)
- Cambiare `CenterAlignedTopAppBar` → `TopAppBar`

#### `simple-transcription-app`
- `SettingsScreen.kt`: aggiungere nella sezione esistente **Updates** un nuovo `SettingItem` (o `ListItem`) per la versione
  - La sezione "About" (informazioni app) può essere aggiunta sotto Updates
  - `BuildConfig` già disponibile, nessuna modifica al `build.gradle.kts`

#### `simple-shortcut-app`
- ✅ Già conforme allo stile `ListItem` + ha già la versione
- Nessuna modifica alle Settings

---

## 2. AppSelectorSheet — Live Selection (`simple-shortcut-app`)

### Problema
Il bottone OK conferma la selezione ma non chiude il sheet, lasciando l'utente in uno stato ambiguo.

### Soluzione: Live Selection

La selezione viene propagata immediatamente al parent a ogni toggle, eliminando lo stato locale duplicato nel sheet.

#### Firma aggiornata

```kotlin
// Prima (con stato locale + OK/Cancel)
AppSelectorSheet(
    alreadySelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
)

// Dopo (live selection)
AppSelectorSheet(
    selectedPackages: List<String>,   // stato del parent, passato in sola lettura
    onToggle: (String) -> Unit,       // chiamato ad ogni tap → aggiorna il parent
    onDismiss: () -> Unit
)
```

#### Comportamento

- Ogni tap su una riga chiama immediatamente `onToggle(packageName)`
- Il parent (`ShortcutEditorScreen`) aggiorna `selectedPackages` in risposta
- Il sheet non ha più i pulsanti **OK** e **Cancel** nella parte bassa
- Al loro posto: un singolo pulsante **"Chiudi"** (o solo il drag nativo del `ModalBottomSheet`)
- La lista di app selezionate nell'editor si aggiorna in tempo reale mentre il sheet è aperto

#### Modifiche ai file

- `AppSelectorSheet.kt`: nuova firma, rimozione stato locale `selected`, rimozione Row con OK/Cancel, aggiunta pulsante "Chiudi"
- `ShortcutEditorScreen.kt`: aggiornare la chiamata ad `AppSelectorSheet` con la nuova firma:
  ```kotlin
  AppSelectorSheet(
      selectedPackages = selectedPackages,
      onToggle = { pkg ->
          selectedPackages = if (pkg in selectedPackages)
              selectedPackages - pkg
          else
              selectedPackages + pkg
      },
      onDismiss = { showAppSelector = false }
  )
  ```

---

## Verifica

- **document-scanner**: aprire Settings → verificare stile `ListItem`, presenza voce versione con valore corretto
- **transcription-app**: aprire Settings → verificare presenza voce versione
- **shortcut-app**: creare una shortcut → aprire il sheet app → toccare app → verificare che la checkbox si aggiorni e che la lista nell'editor si aggiorni in tempo reale → chiudere con "Chiudi" → verificare che le app selezionate siano salvate correttamente
