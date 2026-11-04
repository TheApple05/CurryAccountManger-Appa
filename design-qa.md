# Indigo menu update — Minecraft 1.21.11

The 5.0-backport.3 build uses the same nine-role indigo palette as the four other
Minecraft ports. The supplied hex values define the visual target; Minecraft's
existing type, layout, icons, and input behavior are retained.

- [Account screen](releases/1.21.11/02-offline-account.png).
- [Focused input](releases/1.21.11/07-field-focus.png).
- [Hovered primary action](releases/1.21.11/08-primary-hover.png).
- [Runtime verdict](releases/1.21.11/validation.json) and
  [exact screenshot color evidence](releases/1.21.11/palette-colors.json).

Background `#06070B`; panels and idle controls `#101114`; fields `#000000`;
primary actions and selected tabs `#1A3BD1`; default outlines `#293C94`;
focused outlines and the active username `#526FFF`; hover outlines `#8181D9`;
headings/button text `#FCFCFC`; placeholders and empty-state text `#C8C9D1`.
The primary button fill stays deep blue during hover. Existing destructive labels
and confirmation dialogs identify account-removal actions.

The following sections preserve the review history of the earlier bowl theme.

---

# Account manager palette review

Workflow: Product Design plugin, using its context, design QA, and screenshot
comparison guidance. The target is the existing native Minecraft account manager.

## Evidence and comparison scope

- Source visual truth: [supplied palette](docs/design/palette-reference.png),
  1120 × 572 pixels. The labeled hex values are authoritative.
- Implementation: [account screen](releases/1.21.11/03-renamed-and-restored.png),
  1280 × 720 pixels, Minecraft GUI scale 2 (640 × 360 logical units).
- Full-view comparison: [reference and implementation together](docs/design/final-comparison.png).
- Focused comparison: [fields, hover, and popups](docs/design/final-states.png).
- Additional states: multiplayer entry point, empty/saved accounts, notifications,
  search, focused input, generator, clear confirmation, and primary-action hover.
- Compact native window: [840 × 520 capture](releases/1.21.11/09-compact-window.png),
  420 × 260 logical units. Existing list-only compact behavior is retained.
- CSS size/deviceScaleFactor: not applicable to a native Minecraft screen.
  Native screenshots are retained at their original pixel size. The palette card
  is proportionally reduced to 400 pixels high only in the comparison boards.

The source is a color specification, not a screen mockup. Compare palette,
hierarchy, contrast, and states against it; do not infer layout or font changes
from the palette card's typography or proportions.

## Findings and iteration history

1. First comparison: **blocked**.
   [Main screen](docs/design/pass-1.png) and [popups](docs/design/pass-1-popups.png)
   were reviewed with the source palette in the same image input.
   - [P2] Selected account-type tabs used success green, making selection and
     successful login look equivalent. Changed selected tabs to teal with a
     golden outline; green is reserved for success/toggles.
   - [P2] Clearing the input fields used the same orange warning treatment as
     deleting saved accounts. Changed that control to a quiet navy secondary
     button; destructive account operations retain orange.
2. Second comparison: [corrected selection and action hierarchy](docs/design/pass-2.png).
   The two earlier findings were visibly resolved. Hover contrast measurements
   found a remaining [P2]: white hover overlays reduced primary text to 4.41:1,
   success text to 4.45:1, and destructive icons to 2.61:1. Reduced hover-overlay
   opacity while retaining visible hover feedback.
3. Final comparison: final full-view and focused evidence above were opened and
   reviewed together with the source. No actionable P0/P1/P2 findings remain.

## Required fidelity surfaces

- **Fonts/typography:** existing Minecraft pixel font and sizes retained. Labels,
  field hints, account names, and popup text remain readable; no new font or
  artificial type treatment was introduced.
- **Spacing/layout:** existing panel proportions, control positions, icon sizes,
  and popup geometry retained. Themed inputs restore vanilla geometry before
  handling input; mouse cursor positioning passed the native runtime check.
- **Colors/tokens:** the seven reference colors are named in `AccountPalette`.
  Navy shades support readable surfaces; electric blue marks primary actions;
  teal marks controls/selection; gold marks focus/current identity; herb green
  marks success; orange marks destructive actions; brown accents scrollbars.
  [Ten contrast measurements](docs/design/contrast.json) passed, including hover
  states: at least 4.5:1 for the measured text and 3:1 for the measured icons and
  control boundaries. Disabled controls are intentionally quieter.
- **Image quality/assets:** original icons and actual Minecraft skin rendering
  retained. Screenshots show crisp assets without replacement drawings. The bowl
  illustration is a palette reference, not a requested new in-game logo.
- **Copy/content:** account labels, action wording, and confirmation copy remain
  unchanged. No palette names or implementation details were added to the UI.

## Runtime and limitations

The exact `5.0-backport.2+1.21.11` JAR passed 17 checks on Java 21 with Fabric in
production mode, including offline account operations, disk persistence,
session restoration, field cursor positioning, popup opening, and native window
resize. Ten native screenshots were captured. There were no rendering or mixin
failures; synthetic-account authentication/profile lookup warnings remain
expected. Real Microsoft/session-token/Altening authentication, other mods,
other operating systems, and accessibility beyond the listed visual checks
were not newly tested.

Implementation checklist complete: shared tokens, normal/selected/disabled/hover
states, fields/focus, icons, notifications, popups, entry-point button, packaged
runtime validation, and final reference comparison. No follow-up P3 polish is
required for this palette change.

final result: passed
