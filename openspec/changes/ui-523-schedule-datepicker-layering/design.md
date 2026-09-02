## Context

The Schedule date/time picker currently renders its popup inside a wizard panel.
The panel's visual effects establish a stacking context, so the popup cannot
appear above a later sibling action panel even when its local z-index is raised.

## Goals / Non-Goals

**Goals:**

- Keep the existing inline calendar and time-list interaction.
- Ensure the popup has a safe application-level layer when it overlaps wizard
  controls.
- Verify the popup is mounted outside the panel stacking context.

**Non-Goals:**

- Redesigning Schedule fields or changing their stored local-date-time format.
- Changing the picker locale, time interval, or using a modal date picker.

## Decisions

### Render the popup through a document-body portal

The date-picker component will use its supported portal target so the popup is
mounted outside the wizard panel's stacking context. Its popper receives a
high, application-safe z-layer class.

Raising z-index only on the current inline popper was rejected because z-index
cannot escape its ancestor stacking context. The library's full-screen portal
mode was rejected because it changes the existing inline picker into a modal
experience.

## Risks / Trade-offs

- [A portal changes the popup's DOM location] → Keep input labelling and picker
  callbacks unchanged; assert the portal mount in the component test.
- [Visual stacking cannot be measured in jsdom] → Test the portal/layer contract
  and manually confirm the overlapping wizard-footer case in the browser.

## Migration Plan

Deploy with the frontend bundle. The change has no persisted state or backend
contract, and rollback is a frontend-only revert.
