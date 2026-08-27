## Why

The data-source detail regression test supplies an incomplete store fixture.
When the page reads synthetic-start state, the test throws before verifying the
Run action.

## What changes

- Add the required synthetic-start state to the test fixture.
- Assert that the Run action remains usable when no start request is pending.

## Non-goals

- Do not change production runtime behavior or the data-source store contract.
