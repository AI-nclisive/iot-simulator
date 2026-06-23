# IoT Data Source Simulator Capabilities

## Epic: Data Sources

### Capability: Simulate OPC UA Data Sources

Explanation:
Users can create and run simulated OPC UA data sources so Edge Devices can connect to them as if they were real industrial instruments.

Priority:
P0

Implementation Status:
ToDo

### Capability: Simulate Modbus TCP Data Sources

Explanation:
Users can create and run simulated Modbus TCP data sources so Edge Devices can be tested against common industrial protocol integrations.

Priority:
P0

Implementation Status:
ToDo

### Capability: Run Multiple Data Sources Concurrently

Explanation:
Users can run multiple simulated data sources at the same time to reproduce realistic Edge Device environments where one device consumes data from several instruments.

Priority:
P1

Implementation Status:
ToDo

### Capability: Manage Data Sources On Demand

Explanation:
Users can add, start, stop, and remove simulated data sources whenever needed, without recreating the whole simulator setup.

Priority:
P0

Implementation Status:
ToDo

### Capability: Create Data Source From Real Source Scan

Explanation:
Users can scan a real data source and create a simulated data source from the discovered structure, reducing manual setup and improving similarity to real instruments.

Priority:
P0

Implementation Status:
ToDo

### Capability: Manually Create Data Source Schemas

Explanation:
Users can manually define the structure of a simulated data source when real instruments are unavailable, sensitive, incomplete, or not yet built.

Priority:
P1

Implementation Status:
ToDo

### Capability: Save And Reuse Simulator Projects

Explanation:
Users can save a complete simulator setup and reopen it later, making regression environments repeatable across development and QA workflows.

Priority:
P0

Implementation Status:
ToDo

### Capability: Import And Export Simulator Projects

Explanation:
Users can export a simulator setup and import it into another environment, supporting collaboration, bug reports, support handoff, and reuse between local and shared usage.

Priority:
P1

Implementation Status:
ToDo

### Capability: Manage Simulator Projects

Explanation:
Users can rename, duplicate, archive, and delete simulator projects so project lists stay organized and obsolete setups can be retired without recreating other work.

Priority:
P1

Implementation Status:
ToDo

## Epic: Recordings, Samples, Replay, And Scenarios

### Capability: Record Real Data

Explanation:
Users can record data from a real data source so real instrument behavior can be preserved and reused for debugging and regression testing.

Priority:
P0

Implementation Status:
ToDo

### Capability: Store Multiple Recordings And Samples

Explanation:
Users can keep multiple recordings and samples for the same data source, covering different operating modes, product states, warning states, and failure states.

Priority:
P1

Implementation Status:
ToDo

### Capability: Replay Recorded Data

Explanation:
Users can replay previously recorded real data through simulated data sources to reproduce bugs and validate that fixed behavior does not regress.

Priority:
P0

Implementation Status:
ToDo

### Capability: Generate Synthetic Data

Explanation:
Users can generate synthetic values for simulated data sources when real recordings do not exist, boundary cases are needed, or deterministic test data is required.

Priority:
P1

Implementation Status:
ToDo

### Capability: Build Custom Scenarios

Explanation:
Users can combine recordings, samples, synthetic data, timing, replay order, and faults into a custom scenario that represents a meaningful test flow.

Priority:
P2

Implementation Status:
ToDo

### Capability: Run Deterministic Scenarios

Explanation:
Users can run synthetic data, recorded data replay, and custom scenarios in a deterministic way so repeated runs produce the same values, event order, and timing behavior.

Priority:
P1

Implementation Status:
ToDo

### Capability: Simulate Faults And Unreliable Conditions

Explanation:
Users can simulate bad values, missing values, delayed responses, connection drops, timeouts, protocol errors, and unavailable data sources to validate Edge Device resilience and recovery behavior.

Priority:
P2

Implementation Status:
ToDo

## Epic: Observability And Evidence

### Capability: Observe Enabled And Running Data Sources

Explanation:
Users can see which data sources exist, which are enabled, and which are currently running before and during tests.

Priority:
P0

Implementation Status:
ToDo

### Capability: Observe Connected Clients

Explanation:
Users can see which Edge Devices or clients are connected to each simulated data source, confirming that devices are connected to the expected sources.

Priority:
P1

Implementation Status:
ToDo

### Capability: Observe Live Data Values

Explanation:
Users can view simulated data values in real time to debug mismatches between simulator behavior and Edge Device behavior.

Priority:
P0

Implementation Status:
ToDo

### Capability: Observe Data Source Health And Errors

Explanation:
Users can see health state and errors for simulated data sources to understand whether a problem comes from the simulator, source configuration, protocol behavior, or Edge Device behavior.

Priority:
P1

Implementation Status:
ToDo

### Capability: Observe Runtime Event History

Explanation:
Users can see runtime events such as source start, source stop, client connect, client disconnect, replay start, replay stop, scenario step changes, and faults.

Priority:
P1

Implementation Status:
ToDo

### Capability: Observe User Activity History

Explanation:
Users can see which user performed which action, such as creating, editing, running, stopping, or deleting projects, data sources, recordings, scenarios, and evidence, providing accountability in shared environments. This is distinct from runtime event history, which records simulator runtime events rather than user actions.

Priority:
P2

Implementation Status:
ToDo

### Capability: Export Run Evidence

Explanation:
Users can export a portable evidence artifact from manual or automated simulator runs, including value timelines, client connection history, scenario metadata, runtime events, faults, and errors.

Priority:
P0

Implementation Status:
ToDo

## Epic: User Interface And Control

### Capability: Use Web UI

Explanation:
Users can manage simulations and observe runtime behavior manually through a Web UI, making the simulator accessible for QA and shared team workflows.

Priority:
P0

Implementation Status:
ToDo

### Capability: Control Simulations From Automated Tests

Explanation:
Users can control simulations from automated regression flows by starting simulator projects, running data sources and scenarios, checking readiness and runtime state, and stopping simulations after tests complete.

Priority:
P1

Implementation Status:
ToDo

## Epic: Local And Shared Usage

### Capability: Use Product Locally

Explanation:
Users can run and use the simulator on their own workstation to reproduce bugs, validate changes, and develop Edge Device behavior without depending on shared environments.

Priority:
P0

Implementation Status:
ToDo

### Capability: Use Product As Shared Team Environment

Explanation:
Users can access a shared simulator environment used by multiple QA and engineering team members for team-level regression testing, collaboration, release validation, and shared debugging.

Priority:
P1

Implementation Status:
ToDo

### Capability: Edit Shared Projects Safely

Explanation:
Users can edit shared projects, data sources, and scenarios without silently overwriting each other's changes, with other users seeing a read-only view while an item is being edited.

Priority:
P2

Implementation Status:
ToDo

### Capability: Use Product On Linux, Windows, And macOS

Explanation:
Users can use the simulator on Linux, Windows, and macOS, reducing environment friction across QA and engineering teams.

Priority:
P1

Implementation Status:
ToDo

## Epic: Login And Access Control

### Capability: Use Product Without Login In Trusted Local Scenarios

Explanation:
Users can use the simulator without authentication in trusted local scenarios so local development and bug reproduction remain fast and low-friction.

Priority:
P0

Implementation Status:
ToDo

### Capability: Use Login In Shared Environments

Explanation:
Users can sign in when the simulator is used as a shared team environment, protecting shared projects, active simulations, recordings, scenarios, and evidence.

Priority:
P1

Implementation Status:
ToDo

### Capability: Sign In With External Identity Providers

Explanation:
Users can sign in with Keycloak, AWS Cognito, or Azure Entra ID, allowing shared environments to use common enterprise identity providers without requiring product-owned local accounts.

Priority:
P2

Implementation Status:
ToDo

### Capability: Assign User Roles In Shared Environments

Explanation:
Users can be assigned roles such as admin or user. Users can run and stop simulations, observe simulations and evidence, admins can manage access, modify simulator projects and scenarios.

Priority:
P2

Implementation Status:
ToDo
