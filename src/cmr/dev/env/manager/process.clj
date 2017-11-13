(ns cmr.dev.env.manager.process
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as shell]
    [taoensso.timbre :as log]))

(defn shell!
  [& args]
  (log/trace "shell! args:" args)
  (let [results (apply shell/sh args)
        out (:out results)
        err (:err results)]
    (when (seq out)
      (log/debug out))
    (when (seq err)
      (log/error err))))

(defn spawn!
  [& args]
  (async/thread (apply shell! args)))
