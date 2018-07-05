(ns cmr.opendap.components.concept
  "This namespace represents the 'concept query' API for CMR OPeNDAP. This is
  where the rest of the application goes when it needs to perform a query to
  CMR to get concept data. This is done in order to cache concepts and use
  these instead of making repeated queries to the CMR."
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.ous.collection :as collection]
   [cmr.opendap.util :as util]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concept-key
  [id-or-ids]
  (if (coll? id-or-ids)
    (str "concepts:" (string/join "," id-or-ids))
    (str "concept:" id-or-ids)))

(defn- -get-cached
  "This does the actual work for the cache lookup and fallback function call."
  [system concept-id lookup-fn lookup-args]
  (log/trace "lookup-fn:" lookup-fn)
  (log/trace "lookup-args:" lookup-args)
  (try
    (caching/lookup
     system
     (concept-key concept-id)
     #(apply lookup-fn lookup-args))
    (catch Exception e
      (log/error e)
      {:errors (errors/exception-data e)})))

(defn get-cached
  "Look up the concept for a concept-id in the cache; if there is a miss,
  make the actual call for the lookup.

  Due to the fact that the results may or may not be a promise, this function
  will check to see if the value needs to be wrapped in a promise and will do
  so if need be."
  [system concept-id lookup-fn lookup-args]
  (let [maybe-promise (-get-cached system concept-id lookup-fn lookup-args)]
    (if (util/promise? maybe-promise)
      (do
        (log/trace "Result identifed as promise ...")
        maybe-promise)
      (let [wrapped-data (promise)]
        (log/trace "Result is not a promise ...")
        (deliver wrapped-data maybe-promise)
        wrapped-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Caching Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti get (fn [concept-type & _]
  (log/trace "Dispatching on concept type:" concept-type)
  concept-type))

(defmethod get :collection
  [_type system search-endpoint user-token params]
  (get-cached system
              (:concept params)
              collection/async-get-metadata
              [search-endpoint user-token params]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Concept [])

(defn start
  [this]
  (log/info "Starting concept component ...")
  (log/debug "Started concept component.")
  this)

(defn stop
  [this]
  (log/info "Stopping concept component ...")
  (log/debug "Stopped concept component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Concept
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Concept {}))
