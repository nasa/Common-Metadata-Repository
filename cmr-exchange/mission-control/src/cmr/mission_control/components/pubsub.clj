(ns cmr.mission-control.components.pubsub
  (:require
    [clojure.core.async :as async]
    [cmr.mission-control.components.config :as config]
    [cmr.mission-control.components.util :as util]
    [cmr.mission-control.message :as message]
    [cmr.mission-control.pubsub.core :as pubsub]
    [cmr.mission-control.tag :as tag]
    [cmr.mission-control.topic :as topic]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants, Data, & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-subscribers
  {:dataflow tag/generic [:default]
   :dataflow tag/subscribers-added [:default]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   PubSub Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-subscriber
  [system msg]
  (log/debug "Debug subscriber got system with keys:" (keys system))
  (log/debug "Debug subscriber got msg payload:" (message/get-payload msg)))

(defn trace-subscriber
  [system msg]
  (log/trace "Trace subscriber got system:" system)
  (log/trace "Trace subscriber got full msg:" msg))

(defn get-pubsub
  ""
  ([system]
   (:pubsub system))
  ([system type]
   (get-in system [:pubsub type])))

(defn get-dataflow-pubsub
  ""
  [system]
  (get-pubsub system :dataflow))

(defn get-notifications-pubsub
  ""
  [system]
  (get-pubsub system :notifications))

(defn publish
  ""
  ([system event-type]
   (publish system event-type {}))
  ([system event-type data]
   (publish system :dataflow event-type data))
  ([system pubsub-type event-type data]
   (if (nil? system)
     (log/error "System cannot be nil!")
     (let [system (util/pubsub-component->system system)
           pbsb (get-pubsub system pubsub-type)
           topic (pubsub/get-topic pbsb)
           msg (message/new-event topic event-type data)]
       (log/debug "\tPublishing message to" (message/get-route msg))
       (log/trace "Sending message data:" (message/get-payload msg))
       (async/>!! (pubsub/get-chan pbsb) msg)))
   data))

(defn publish->
  ""
  ([other-data system event-type]
   (publish-> other-data system event-type {}))
  ([other-data system event-type data]
   (publish-> other-data system :dataflow event-type data))
  ([other-data system pubsub-type event-type data]
   (publish system pubsub-type event-type data)
   other-data))

(defn publish->>
  ""
  ([system event-type other-data]
   (publish->> system event-type {} other-data))
  ([system event-type data other-data]
   (publish->> system :dataflow event-type data other-data))
  ([system pubsub-type event-type data other-data]
   (publish system pubsub-type event-type data)
   other-data))

(defn default-subscribe-fn
  [s m]
  (log/warn
   "Using default subscriber callback for"
   (message/get-route m)))

(defn subscribe
  ""
  ([system event-type]
   (subscribe system event-type nil))
  ([system event-type func]
   (subscribe system :dataflow event-type func))
  ([system pubsub-type event-type func]
   (when-not (nil? system)
     (let [pbsb (get-pubsub system pubsub-type)]
       (async/go-loop []
         (when-let [msg (async/<! (pubsub/get-sub pbsb event-type))]
           (log/debug "Received subscribed message for" (message/get-route msg))
           (log/trace "Message data:" (message/get-payload msg))
           (log/trace "Callback function:" func)
           (func system msg)
           (recur)))))))

(defn subscribe-all-event
  ""
  ([system event-type subscriber-funcs]
   (subscribe-all-event system :dataflow event-type subscriber-funcs))
  ([system pubsub-type event-type subscriber-funcs]
   (doseq [func subscriber-funcs]
     (log/debugf "\tSubscribing to %s ..." event-type)
     (subscribe system pubsub-type event-type debug-subscriber)
     (subscribe system pubsub-type event-type trace-subscriber)
     (if (= func :default)
       (subscribe system pubsub-type event-type default-subscribe-fn)
       (subscribe system pubsub-type event-type func)))))

(defn subscribe-all
  ""
  [system subscribers]
  (let [system (util/pubsub-component->system system)]
    (doseq [[pubsub-type event-type subscriber-funcs] subscribers]
      (subscribe-all-event system
                           pubsub-type
                           event-type
                           subscriber-funcs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord PubSubComponent [
  subscribers
  ;; The 'dataflow' pubsub is intended for use in tracking low-level events
  ;; in the system that relate to the manner in which data flows through the
  ;; system.
  dataflow
  ;; The 'notifications' pubsub is intended for use by anything that needs to
  ;; handle events that take place in wider context of the running system.
  notifications])

(defn start
  [this]
  (log/info "Starting pub-sub component ...")
  (let [dataflow (pubsub/create-dataflow-pubsub
                  (config/messaging-type this))
        notifications (pubsub/create-notifications-pubsub
                       (config/messaging-type this))
        component (assoc this :dataflow dataflow
                              :notifications notifications)]
    (log/info "Adding subscribers ...")
    (subscribe-all component (:subscribers this))
    (log/debug "Started pub-sub component.")
    component))

(defn stop
  [this]
  (log/info "Stopping pub-sub component ...")
  (when-let [pubsub-dataflow (:dataflow this)]
    (pubsub/delete pubsub-dataflow))
  (when-let [pubsub-notifications (:notifications this)]
    (pubsub/delete pubsub-notifications))
  (let [component (assoc this :dataflow nil
                              :notifications nil)]
    (log/debug "Stopped pub-sub component.")
    component))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend PubSubComponent
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  ([]
   (create-component {}))
  ([subscribers]
   (map->PubSubComponent
    {:subscribers (merge default-subscribers subscribers)})))
