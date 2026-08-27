## Approach

Keep the production store unchanged. Extend the local test-state type and its
default fixture with the existing `startingSyntheticIds` field. The synthetic
source test will then exercise the page using the same store shape that the
page consumes in production.

## Spec impact

No behavior changes. This restores test coverage of the existing Data Source
Detail action contract, so no specification delta is required.
