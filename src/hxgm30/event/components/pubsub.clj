(ns hxgm30.event.components.pubsub
  (:require
    [clojure.core.async :as async]
    [com.stuartsierra.component :as component]
    [hxgm30.event.components.config :as config]
    [hxgm30.event.components.util :as util]
    [hxgm30.event.message :as message]
    [hxgm30.event.pubsub.core :as pubsub]
    [hxgm30.event.tag :as tag]
    [hxgm30.event.topic :as topic]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants, Data, & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-subscribers
  {tag/generic [:default]
   tag/subscribers-added [:default]})

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
  [system]
  (get-in system [:pubsub]))

(defn get-dataflow-pubsub
  ""
  [system]
  (get-in system [:pubsub :dataflow]))

(defn publish
  ""
  ([system event-type]
   (publish system event-type {}))
  ([system event-type data]
   (if (nil? system)
     (log/error "System cannot be nil!")
     (let [system (util/pubsub-component->system system)
           dataflow (get-dataflow-pubsub system)
           topic (pubsub/get-topic dataflow)
           msg (message/new-dataflow-event event-type data)]
       (log/debug "\tPublishing message to" (message/get-route msg))
       (log/trace "Sending message data:" (message/get-payload msg))
       (async/>!! (pubsub/get-chan dataflow) msg)))
   data))

(defn publish->
  ""
  ([other-data system event-type]
   (publish-> other-data system event-type {}))
  ([other-data system event-type data]
   (publish system event-type data)
   other-data))

(defn publish->>
  ""
  ([system event-type other-data]
   (publish->> system event-type {} other-data))
  ([system event-type data other-data]
   (publish system event-type data)
   other-data))

(defn subscribe
  ""
  ([system event-type]
   (subscribe system event-type (fn [s m]
                                  (log/warn
                                   "Using default subscriber callback for"
                                   (message/get-route m)))))
  ([system event-type func]
   (when-not (nil? system)
     (let [dataflow (get-dataflow-pubsub system)]
       (async/go-loop []
         (when-let [msg (async/<! (pubsub/get-sub dataflow event-type))]
           (log/debug "Received subscribed message for" (message/get-route msg))
           (log/trace "Message data:" (message/get-payload msg))
           (log/trace "Callback function:" func)
           (func system msg)
           (recur)))))))

(defn subscribe-all-event
  ""
  [system event-type subscriber-funcs]
  (doseq [func subscriber-funcs]
    (log/debugf "\tSubscribing to %s ..." event-type)
    (subscribe system event-type debug-subscriber)
    (subscribe system event-type trace-subscriber)
    (if (= func :default)
      (subscribe system event-type)
      (subscribe system event-type func))))

(defn subscribe-all
  ""
  [system subscribers]
  (let [system (util/pubsub-component->system system)]
    (doseq [[event-type subscriber-funcs] subscribers]
      (subscribe-all-event system
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
  ;; The 'world' pubsub is intended for use by anything that needs to handle
  ;; events that take place in the game world.
  world])

(defn start
  [this]
  (log/info "Starting pub-sub component ...")
  (log/debug "Started pub-sub component.")
  (let [dataflow (pubsub/create-dataflow-pubsub
                  (config/event-system-type this))
        world (pubsub/create-dataflow-pubsub
               (config/event-system-type this))
        component (assoc this :dataflow dataflow
                              :world world)]
    (log/info "Adding subscribers ...")
    (subscribe-all component (:subscribers this))
    component))

(defn stop
  [this]
  (log/info "Stopping pub-sub component ...")
  (when-let [pubsub-dataflow (:dataflow this)]
    (pubsub/delete pubsub-dataflow))
  (when-let [pubsub-world (:world this)]
    (pubsub/delete pubsub-world))
  (let [component (assoc this :dataflow nil
                              :world nil)]
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
   (map->PubSubComponent (merge default-subscribers subscribers))))
