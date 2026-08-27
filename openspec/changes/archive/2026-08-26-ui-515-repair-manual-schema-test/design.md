## Approach

Replace the obsolete two-step interaction in the regression test with the
current `Add data type` action. The existing assertions continue to verify
that a data type has no parent folder and is created successfully.

## Spec impact

The product behavior is unchanged; this repair updates test coverage after a
previous UI control rework. No specification delta is required.
