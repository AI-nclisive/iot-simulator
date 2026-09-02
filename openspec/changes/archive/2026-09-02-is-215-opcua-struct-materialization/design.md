## Context

The OPC UA worker receives native data-type declarations with `Configure` and
constructs its schema namespace during `Start`. `SchemaNamespace` currently
throws while creating a variable whose custom data type has no matching native
declaration; the exception is outside the server runtime's startup error
handling and the service's Start RPC only handles port binding failures.

## Goals / Non-Goals

**Goals:**

- Keep a malformed or incomplete native declaration from preventing unrelated
  OPC UA variables from being simulated.
- Preserve the original native type identity in an operator-visible warning.
- Exercise the result through the actual loopback gRPC Start path.

**Non-Goals:**

- Infer, coerce, or fabricate an OPC UA structure declaration.
- Change persisted schemas, the worker proto, or the runtime event protocol.
- Make other address-space validation errors tolerant.

## Decisions

### Preflight unsupported variables during namespace materialization

The namespace will identify variables that point at an unavailable
non-standard data type before building their nodes, omit those variables, and
report their node id and type through a warning callback. This makes omission
explicit and leaves the address space internally consistent.

The alternative is catching `IllegalArgumentException` around each node build.
That risks leaving partially registered nodes or references behind, and does
not make dependent-node treatment explicit.

### Adapt the namespace warning to the existing runtime-event stream

`OpcUaServerRuntime` will translate namespace warnings into the existing
`RuntimeEvent` representation and send them through its configured sink. This
does not require a wire-contract extension and keeps protocol-specific details
inside the OPC UA worker.

The alternative is exposing the namespace map directly to the service. That
would couple address-space construction to gRPC service state without adding
observable capability.

### Retain a Start RPC safety boundary

The worker Start handler will convert unexpected runtime-start failures into a
negative acknowledgement and an error event rather than allowing gRPC to turn
them into `UNKNOWN`. This covers remaining materialization failures without
misclassifying them as port-bind failures.

## Risks / Trade-offs

- [A variable child refers to an omitted variable] → Omit the dependent node
  too with its own warning, so an omitted parent does not turn into a separate
  fatal parent-validation error.
- [A runtime warning stream is not subscribed yet] → Startup still succeeds;
  the existing runtime-event hub's delivery semantics remain unchanged.
- [A real schema relies on an opaque value] → The warning contains the stable
  node and native type identifiers needed to correct or rescan the schema.
