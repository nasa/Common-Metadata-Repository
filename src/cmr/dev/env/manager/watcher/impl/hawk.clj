(ns cmr.dev.env.manager.watcher.impl.hawk
  (:require
    [cmr.dev.env.manager.messaging.core :as messaging]
    [hawk.core :as hawk]
    [taoensso.timbre :as log]
    [trifl.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clj?
  [_context event]
  (when (= :clj (fs/extension (:file event)))
    true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Watcher Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord HawkWatcher
  [service-key
   messenger])

(defn handle-event
  [this event]
  (let [messenger (:messenger this)]
    (messaging/publish messenger :file-event {:service (:service-key this)
                                              :event event})
    (case (:kind event)
      :modify (messaging/publish messenger :file-modify-event event)
      :create (messaging/publish messenger :file-create-event event)
      :delete (messaging/publish messenger :file-delete-event event)
      (log/warn "Unhandled file system event type:" (:kind event)))
    this))

(defn add-paths
  [this paths]
  (hawk/watch! [{:context (constantly this)
                 :paths paths
                 :filter clj?
                 :handler handle-event}]))

(defn add-path
  [this path]
  (add-paths this [path]))

(def behaviour
  {:handle-event handle-event
   :add-path add-path
   :add-paths add-paths})

(defn create-watcher
  [service-key messenger]
  (map->HawkWatcher {
    :service-key service-key
    :messenger messenger}))
