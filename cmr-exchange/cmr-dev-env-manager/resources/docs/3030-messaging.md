#  • Simple Messaging Between Components

When the CMR D.E.M. system is started up, one of the components is one called
"messaging" -- this is used for both broadcasting messages between components
in a running system as well as subscribing to such messages.

After the messaging component comes up, another component is started:
the "default subscribers" component. This component sets up the default set
of subscribers that come with the D.E.M. out of the box (mostly logging
functionality that supports communication from other processes).

By publishing a message in the REPL to a subscribed topic, you can witness
the interaction of the messaging system with the rest of D.E.M. components:


## Publish a Message to Default Subscribers

```clj
[cmr.dev.env.manager.repl] λ=> (publish-message :warn "Hey, this is a warning message ...")
```

```clj
:ok
2017-12-06T13:04:57.455 [async-dispatch-2] WARN c.d.e.m.c.system:21 - Hey, this is a warning message ...
```

```clj
[cmr.dev.env.manager.repl] λ=> (publish-message :fatal "Look OUT!!!!!")
```

```clj
:ok
2017-12-06T13:05:27.254 [async-dispatch-5] FATAL c.d.e.m.c.system:19 - Look OUT!!!!!
```


## Created a Custom Subscriber

The CMR D.E.M. is a dynamic system, one that you can interact with and update
while it's running. Either by entering commands in the REPL or by adding new
features to the D.E.M., you can change how the D.E.M. behaves by publishing
new messages, or subscribing to messages.

As an example of this, let's create our own, silly subscriber:

```clj
[cmr.dev.env.manager.repl] λ=> (defn subscriber
                                 [content]
                                 (println "Got:" content))
```

```clj
[cmr.dev.env.manager.repl] λ=> (subscribe-message :my-topic subscriber)
```


## Publish a Message to Custom Subscriber

```clj
[cmr.dev.env.manager.repl] λ=> (publish-message
                                :my-topic
                                "And now, for something completely different ...")
```

```clj
:ok
Got: And now, for something completely different ...
```

We could also have supplemented an existing topic by calling
`subscribe-message` with an existing topic, e.g., one of the logging ones such
as `:warn`. Publishing a message to the `:warn` topic would then have resulted
in not only the logging of the original "warn" message, but also in the
printing to `stdout` of our `Got: ...` message.
