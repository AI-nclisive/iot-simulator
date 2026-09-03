## 1. Specification and configuration

- [ ] 1.1 Add RTU transport and serial settings to persisted, API, and worker configuration.
- [ ] 1.2 Validate transport-specific settings and preserve TCP defaults for existing sources.
- [ ] 1.3 Add the approved jSerialComm dependency to the worker runtime.

## 2. Worker implementation

- [ ] 2.1 Implement RTU frame transport and serial-port lifecycle management.
- [ ] 2.2 Route serve, connection test, scan, and capture through the selected transport.
- [ ] 2.3 Keep TCP behaviour regression-tested.

## 3. UI and verification

- [ ] 3.1 Add real-source RTU settings to the creation flow.
- [ ] 3.2 Add unit and integration coverage, including serial-independent RTU framing tests.
- [ ] 3.3 Run backend and frontend validation, archive the OpenSpec change, and open the PR.
