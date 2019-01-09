#  • Inspecting Docker-based Services

As with the process-based services, there are convenience functions provided
for docker-based services.


## Options and Metadata

Get the configuration options that are passed to the docker functions for
a given docker service:

```clj
[cmr.dev.env.manager.repl] λ=> (get-docker-opts :elastic-search)
```
```clj
{:image-id "elasticsearch:1.6.2",
 :ports ["9200:9200" "9300:9300"],
 :env ["discovery.type=single-node"],
 :container-id-file "/tmp/cmr-dem-elastic-container-id"}
```

Get the container id of a given running docker service:

```clj
[cmr.dev.env.manager.repl] λ=> (get-docker-container-id :elastic-search)
```
```clj
"54505c994f9fbb1d4f4f2c94a0ad2079255a59040bbe7417dc3f27cec01a8003"
```


## State

Get the container state of a given running docker service:

```clj
[cmr.dev.env.manager.repl] λ=> (get-docker-container-state :elastic-search)
```
```clj
{:Restarting false,
 :ExitCode 0,
 :Running true,
 :Pid 3728,
 :StartedAt "2017-12-07T00:13:56.132615951Z",
 :Dead false,
 :Paused false,
 :Error "",
 :FinishedAt "0001-01-01T00:00:00Z",
 :OOMKilled false,
 :Status "running"}
```

You may also call a function to return all of the docker container's data:

```clj
[cmr.dev.env.manager.repl] λ=> (get-docker-container-data :elastic-search)
```

We haven't listed the output, due to its length.


## Health

Get the current health of a docker container:

```clj
[cmr.dev.env.manager.repl] λ=> (check-health :elastic-search)
```

```clj
{:process :ok, :http :ok, :ping :ok, :cpu :ok, :mem :ok}
```

If you'd like to see the health check details:

```clj
[cmr.dev.env.manager.repl] λ=> (check-health-details :elastic-search)
```

```clj
{:docker
 {:status :running,
  :details
  {:Restarting false,
   :ExitCode 0,
   :Running true,
   :Pid 14932,
   :StartedAt "2017-12-12T16:10:39.926725131Z",
   :Dead false,
   :Paused false,
   :Error "",
   :FinishedAt "0001-01-01T00:00:00Z",
   :OOMKilled false,
   :Status "running"}},
 :http {:status :ok},
 :ping {:status :ok},
 :cpu {:status :ok, :details {:value 1.0, :type :percent}},
 :mem {:status :ok, :details {:value 0.7, :type :percent}}}
```
