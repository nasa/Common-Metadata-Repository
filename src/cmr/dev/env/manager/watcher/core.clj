(ns cmr.dev.env.manager.watcher.core
  (:require
    [cmr.dev.env.manager.watcher.impl.darwin :as darwin]
    [cmr.dev.env.manager.watcher.impl.hawk :as hawk]
    [cmr.dev.env.manager.watcher.impl.linux :as linux]
    [cmr.dev.env.manager.watcher.impl.nio :as nio])
  (:import
    (cmr.dev.env.manager.watcher.impl.hawk HawkWatcher)))

(def default-watcher-type :hawk)

(defprotocol Watcher
  (add-path [this path])
  (add-paths [this paths])
  (handle-event [this event]))

(extend HawkWatcher
        Watcher
        hawk/behaviour)

(defn create-watcher
  ([service-key messenger]
    (create-watcher default-watcher-type service-key messenger))
  ([type service-key messenger]
    (case type
      :hawk (hawk/create-watcher service-key messenger))))
