## ADDED Requirements

### Requirement: Wizard date/time picker controls remain available above action panels
The Create Data Source Wizard SHALL render the Schedule step's date/time picker
popup above every wizard action panel that overlaps it, so every date and time
control remains visible and operable.

#### Scenario: Schedule picker overlaps the wizard footer
- **WHEN** a user opens a Schedule step date/time picker whose popup extends
  into the wizard footer
- **THEN** the popup renders above the footer and its date and time controls
  remain available to the user
