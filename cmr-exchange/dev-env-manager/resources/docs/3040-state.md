#  • Working with State

Supporting functions for examining state are also available.

Get the current state of the system:

```clj
[cmr.dev.env.manager.repl] λ=> (get-state)
```
```clj
:running
```

Get the time (in seconds) it took for the more recent state transition:

```clj
[cmr.dev.env.manager.repl] λ=> (get-time)
```
```clj
0.231
```

If the last state-modifying system transition was `(startup)`, then the time
given will reflect how long that took. Likewise if `(shutdown)` or `(restart)`
were called. Right now, these are the only supported calls for time-tracking.

Note that, while certain state-mutating functions are also available, their
intended use is by the system itself or for low-level debugging; best not
ever call those directly.
