# IoT Data Source Simulator UI Design

## Purpose

`DESIGN.md` describes how the Web UI should behave.

It exists to define:

- the structure of the shell;
- the main user flows;
- the interaction rules that must stay consistent across screens;
- the shared-work behavior that makes the product understandable in daily use.

## Document Ownership

- `SPEC.md` owns product capabilities and boundaries.
- `ARCHITECTURE.md` owns technical constraints and system rules.
- `STACK.md` owns approved technologies.
- `UI_PLAN.md` owns staged delivery order.
- `UI_SCREEN_SPECS.md` owns screen-by-screen requirements.
- `UI_TASKS.md` owns implementation tasks.
- `DESIGN.md` owns UI structure, flow, and interaction behavior.

## Scope

This file should describe:

- how users enter and move through the product;
- how the main workflow is presented;
- how editing, runtime, and history are separated in the UI;
- how shared usage changes behavior;
- how the interface handles state, feedback, and risk.

This file should not repeat:

- capability lists;
- implementation status;
- stage planning;
- screen inventory details;
- technical implementation detail.

## Product UX Model

The interface is an operational shell for simulator setup, runtime
observation, and reproducible evidence.

It should feel:

- calm;
- precise;
- efficient;
- trustworthy;
- oriented toward repeated use, not one-time exploration.

The product must present one clear primary story:

`Scan real source -> Record -> Replay`

That path should be visually and structurally favored because it explains why
this simulator is valuable.

Manual schema creation, prepared-file input, and synthetic generation remain
first-class options, but they should appear as alternative setup paths inside
the same product structure rather than as separate mini-products.

## Shell Structure

The UI should behave like a stable shell.

### Global Layout

- minimal top bar for product identity and future utility entry points;
- collapsible left project rail for navigation and project context;
- central work area for the current task.

### Top Bar

The top bar carries the context that should stay visible everywhere:

- product identity;
- lightweight utility entry points when they are needed.

The top bar should stay light. It should not carry launch parameters, data
source setup choices, large evidence blocks, or detailed runtime history.

### Project Rail

The left rail is the home for project context:

- current project identity;
- quick project switching;
- primary navigation.

The rail should remain informative when open and collapse cleanly when users
need more space for detail work.

### Navigation Model

The main navigation should group the product by user intent:

- Overview;
- Data Sources;
- Recordings & Samples;
- Scenarios;
- Evidence;
- Activity;
- Settings;
- Admin.

Navigation labels should use product language only. The UI should never expose
backend or architecture concepts as primary navigation.

### Overview As Command Surface

`Overview` is the operational starting point inside a project.

In the lightest version of the product, the `Overview` route can be the runtime
dashboard itself without extra cards below it.

It should answer these questions immediately:

- what changed recently;
- who started or changed it;
- where there is risk or attention needed;
- where to go next.

`Overview` is not a decorative dashboard. It is the landing point for runtime
awareness and any minimal orientation that is still needed.

### Runtime Dashboard

Live runtime context should be concentrated on `Overview`, where users can
quickly reorient before moving into a deeper task surface.

Users should not lose visibility into:

- active runs;
- active process where relevant, such as recording, replay, or scenario;
- source scale, such as parameter count;
- evidence state inside each active run;
- run initiator or authorship context where relevant;
- short run recency or time context;
- quick links back to the affected object or evidence.

The runtime dashboard should stay compact. Deeper runtime history, alerts, and
operational investigation belong on dedicated runtime, activity, or evidence
surfaces rather than following the user onto every page.

It should summarize large parameter arrays rather than attempt to render them
raw on `Overview`.

If a quick value preview is needed, it should be limited to a small pinned
subset that helps orientation and debugging.

## Entry Flow

The entry experience should be short and orienting.

### Local Entry

In trusted local usage, users should get into work quickly:

- open the product;
- select a project or reopen the last active one;
- land in `Overview` or the last active project surface.

### Shared Entry

In shared usage, the entry flow adds accountability:

- authenticate;
- open the project in the app shell;
- immediately see who is signed in and what the current shared activity looks
  like.

The shift from local to shared mode should change permissions and authorship
visibility, but it should not force users to relearn the whole product layout.

## Core Flow

The primary UX path is:

`Project -> Data Source -> Scan -> Review Schema -> Record -> Replay -> Observe -> Evidence`

This flow should be obvious in empty states, action hierarchy, and detail-page
next steps.

### Project To Source

Users enter a project first. A project groups the simulator setup and its
reusable artifacts.

From the project, the next promoted action is to create or inspect a
data-source.

### Source Creation Model

Source creation should begin from one unified entry point rather than separate
tools per protocol or per origin type.

The first choice is the protocol.

The second choice is the source basis:

- scan a real source;
- create from manual schema;
- import or upload prepared data;
- create from synthetic setup.

This keeps future protocol growth manageable while preserving one mental model
for source creation.

### Wizard Structure

Creation should use one extensible wizard with stable step framing:

1. choose protocol;
2. choose source basis;
3. enter connection, import, or setup details;
4. inspect and refine schema;
5. configure runtime behavior;
6. review and create.

The wizard should stay simple on the surface:

- each step has one job;
- branching happens inside stable steps, not through completely different flows;
- protocol-specific inputs appear only where needed;
- validation is inline and early;
- users can see where they are and what remains.

### Scan Path

The scan path is the most important creation branch and should be visually
recommended.

That flow should make four things clear:

- which real endpoint is being read;
- whether the connection is valid;
- what was discovered;
- what still needs review before creation.

Scan results should move naturally into schema review, not into a dead-end
summary.

### Schema Review And Editing

Creation and editing are different modes and should feel different.

Inside the wizard, schema work is guided and review-oriented.

Outside the wizard, the schema editor is a full editing surface for deliberate
manual control.

The full editor must support:

- structural edits at any depth;
- protocol-relevant field settings;
- bulk navigation and inspection;
- large parameter sets without dumping everything at once;
- clear unsaved-change handling;
- safe behavior under shared editing constraints.

The user should always know whether they are:

- reviewing discovered structure;
- making local edits before create;
- editing an already saved schema.

### Record Path

Once a source exists, the next natural action is to capture real behavior.

The UI should make recording feel like a runtime action with a clear result:

- the user starts recording from a source context;
- the UI shows that recording is active;
- when recording completes, the result becomes a reusable artifact rather than a
  transient success message.

### Replay Path

Replay should feel like a direct continuation of recording, not a separate part
of the product that users must rediscover.

From a saved recording or sample, the user should be able to:

- attach it to a source;
- configure replay behavior;
- start runtime;
- observe the result immediately.

### Observe Path

Observation must clearly separate current runtime from saved history.

The interface should distinguish:

- current live values;
- current runtime state;
- saved recordings and samples;
- exported evidence.

These surfaces may be related, but they must not look interchangeable.

One `data-source` may contain a very large number of parameters inside one run.

The UI should not assume that a source maps to one signal or one small table.

Detail and value surfaces should therefore default to:

- visible total parameter count;
- search, filtering, and grouping before raw browsing;
- dense tables or trees rather than oversized cards;
- partial previews and pinned subsets for fast debugging.

### Evidence Path

Evidence should feel like the natural output of simulator work.

The product should let users move into evidence from:

- the active run context;
- the source detail surface;
- recent activity on `Overview`.

Evidence must keep origin visible:

- what run it came from;
- who initiated that run;
- whether the evidence is complete, partial, or failed.

## Alternative Flows

The product also needs strong non-scan paths.

These should be presented as alternate branches of the same model, not as
secondary or hidden tools.

### Manual Setup

Manual setup is for cases where the user wants to define structure directly.

The UI should keep this path short:

- choose protocol;
- define structure;
- configure runtime;
- save and run when ready.

### Prepared Data Input

Prepared-file input is for cases where the user already has reusable material
and wants to bring it into the simulator without rescanning.

The UI should treat this as a structured import flow, not as a raw file dump.

Users should always see:

- what kind of artifact is being loaded;
- whether it is compatible;
- what will become available after import;
- what is intentionally excluded for safety.

### Synthetic Setup

Synthetic setup should feel deliberate and test-oriented.

This branch should emphasize:

- pattern definition;
- repeatability;
- explicit control rather than realism by default.

## Shared Usage Behavior

The shared experience must be understandable at a glance.

### Role-Aware UI

The interface reacts to system role, not to job title.

Role handling should be visible in action surfaces:

- users can see the same shared project structure;
- available actions change based on permissions;
- unavailable actions should not create confusion about what is happening.

The UI should prefer preventing invalid actions before submit instead of relying
on failure messages after the fact.

### Authorship

Shared activity needs visible authorship.

The interface should surface who initiated:

- a run;
- a recording;
- an evidence export;
- an edit session;
- a disruptive or destructive action.

This information should appear in context, not only in a separate audit page.

### Concurrent Editing

Shared editing must protect against silent overwrite.

The UI should make the current state obvious:

- editable;
- read-only because of role;
- read-only because another user is editing;
- stale because the underlying object changed.

When conflicts happen, the interface should provide a clear next step rather
than a generic failure:

- reload;
- review changes later;
- return to a safe read-only state.

### Destructive Actions

Destructive or disruptive actions always require confirmation.

Confirmation should explain:

- what object is affected;
- whether shared work may be interrupted;
- whether connected devices or users may notice the change;
- whether the action can be undone.

## Interaction Rules

The interface should keep configuration, runtime, and history visually separate.

### Configuration Versus Runtime

Users should never need to guess whether they are:

- changing saved setup;
- observing a running source;
- inspecting historical output.

Action placement, labels, and screen structure should reinforce that difference.

### Live Values Versus Captured Artifacts

Live values are operational and potentially stale.

Recordings, samples, and evidence are persisted artifacts tied to time and
origin.

The UI should label these categories clearly and avoid mixing them inside one
undifferentiated panel.

### Long-Running Operations

Scanning, importing, recording, replay preparation, and evidence assembly should
show durable progress state.

Users should always know:

- what is happening;
- who started it;
- whether it is still in progress;
- whether it is safe to leave the page;
- where the result will appear.

### Failure Handling

The UI should assume partial failure is normal in this domain.

Important screens should account for:

- loading;
- empty state;
- validation problems;
- permission restrictions;
- reconnecting or stale live state;
- partial failure;
- full failure.

Failures should point users toward recovery, not just report that something went
wrong.

### Imports And Exports

Import and export flows should communicate trust and compatibility clearly.

The UI must show:

- artifact type;
- version compatibility;
- expected result after completion;
- omitted sensitive material where relevant.

The interface must never suggest that secrets, credentials, or private keys are
included in exported artifacts.

## Visual Direction

The product should look like an industrial operations tool, not a marketing site
and not a generic analytics dashboard.

The visual system should favor:

- strong hierarchy;
- compact but readable density;
- stable alignment;
- clear status contrast;
- low decorative noise;
- restrained motion used for orientation.

Runtime status, warnings, editability, and authorship should be visible quickly
without forcing users to scan dense prose.

## Accessibility

Accessibility is a baseline requirement.

The interface should support:

- full keyboard navigation;
- visible focus states;
- screen-reader-safe names for actions, dialogs, tabs, and status;
- contrast that preserves status readability;
- status meaning that does not depend only on color;
- responsive behavior that keeps operational surfaces readable.

Complex tables, editors, and live panels need the same accessibility care as
forms and dialogs.

## Responsive Baseline

The product is desktop-first. The primary runtime context is a workstation or
laptop screen. Responsive behavior is a baseline requirement, not a full mobile
redesign.

### Breakpoints

Standard Tailwind CSS breakpoints apply:

| Name | Width    | Behavior                                              |
| ---- | -------- | ----------------------------------------------------- |
| `sm` | >= 640 px | Minor padding and spacing adjustments.                |
| `md` | >= 768 px | Tablet portrait. Layout still stacks vertically.      |
| `lg` | >= 1024 px| Desktop baseline. Two-column shell: sidebar + content.|
| `xl` | >= 1280 px| Wide desktop. Toolbar controls may go side-by-side.   |

### Desktop (lg+)

Full two-column layout. Sidebar is always visible. All operational surfaces are
fully supported. This is the primary supported context.

### Tablet tolerance (md, 768-1023 px)

The shell collapses to a single column. The project rail hides behind a hamburger
toggle in the top bar. Tables stay readable via horizontal scroll. Forms stack
vertically without truncation. Operational use is possible but not optimized.

### Phone limits (<768 px)

The shell is accessible but not optimized for phone-first use. The hamburger
toggle remains available. Dense operational tables scroll horizontally. Forms
remain usable. No phone-specific layouts are planned.

### Browser consistency

The interface must behave consistently on the latest stable versions of Chrome,
Firefox, and Safari across Linux, Windows, and macOS. Internet Explorer is not
supported. Edge (Chromium) behaves equivalent to Chrome.

### Implementation rules

- Tables always wrap in `overflow-x-auto` so dense columns scroll rather than
  truncate or break layout.
- Forms use `flex-col` stacking on narrow screens and may expand to row layouts
  at `xl` or wider.
- The project rail collapses to hidden below `lg`; the hamburger toggle
  (`aria-expanded`, `aria-controls`) makes it accessible via keyboard and touch.
- No minimum viewport width is enforced. The layout degrades gracefully below
  320 px but is not tested at that size.

## Stable UX Decisions

These design decisions are fixed unless the product direction changes:

- the interface is one connected shell, not a set of disconnected tools;
- `Scan real source -> Record -> Replay` is the primary UX path;
- manual, prepared-file, and synthetic paths remain first-class alternatives;
- source creation uses one extensible wizard model;
- schema editing is a full editor, not a limited patch form;
- one `data-source` can carry hundreds or thousands of parameters at once;
- `Overview` is the shared operational command surface;
- shared usage must preserve authorship, concurrency awareness, and explicit
  confirmation for risky actions;
- the UI is role-aware, but behavior must not depend on job title.

## Open Questions

No cross-product UI questions are currently blocking this design document.
