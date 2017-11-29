(ns cmr.dev.env.manager.process.const)

(def ^:dynamic *byte-buffer-size* 1024)
(def ^:dynamic *channel-buffer-size* (* 10 1024))
(def ^:dynamic *read-stream-delay* 5000)
(def ^:dynamic *exit-timeout* 30000)
