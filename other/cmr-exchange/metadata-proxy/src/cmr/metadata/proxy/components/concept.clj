(ns cmr.metadata.proxy.components.concept
  "This namespace represents the 'concept query' API for CMR OPeNDAP. This is
  where the rest of the application goes when it needs to perform a query to
  CMR to get concept data. This is done in order to cache concepts and use
  these instead of making repeated queries to the CMR."
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]
   [cmr.metadata.proxy.components.caching :as caching]
   [cmr.metadata.proxy.components.config :as config]
   [cmr.metadata.proxy.concepts.collection :as collection]
   [cmr.metadata.proxy.concepts.granule :as granule]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn concept-key
  [id]
  (str "concept:" id))

(defn- -get-single-cached
  [system cache-key lookup-fn lookup-args]
  (try
    (caching/lookup
     system
     cache-key
     #(apply lookup-fn lookup-args))
    (catch Exception e
      (log/error e)
      {:errors (errors/exception-data e)})))

(defn- -get-multiple-cached
  [system cache-keys lookup-fn lookup-args]
  (try
    (caching/lookup-many
      system
      cache-keys
      #(apply lookup-fn lookup-args))
    (catch Exception e
      (log/error e)
      {:errors (errors/exception-data e)})))

(defn- -get-cached
  "This does the actual work for the cache lookup and fallback function call."
  ([system cache-key lookup-fn lookup-args]
   (-get-cached system cache-key lookup-fn lookup-args {}))
  ([system cache-key lookup-fn lookup-args opts]
   (let [multi-key? (:multi-key? opts)]
     (log/trace "lookup-fn:" lookup-fn)
     (log/trace "lookup-args:" lookup-args)
     (log/trace "Cache key(s):" cache-key)
     (if multi-key?
       (-get-multiple-cached system cache-key lookup-fn lookup-args)
       (-get-single-cached system cache-key lookup-fn lookup-args)))))

(defn get-cached
  "Look up the concept for a concept-id in the cache; if there is a miss,
  make the actual call for the lookup.

  Due to the fact that the results may or may not be a promise, this function
  will check to see if the value needs to be wrapped in a promise and will do
  so if need be."
  ([system cache-key lookup-fn lookup-args]
   (get-cached system cache-key lookup-fn lookup-args {}))
  ([system cache-key lookup-fn lookup-args opts]
   (let [maybe-promise (-get-cached system
                        cache-key lookup-fn lookup-args opts)]
     (if (util/promise? maybe-promise)
       (do
         (log/trace "Result identifed as promise ...")
         maybe-promise)
       (let [wrapped-data (promise)]
         (log/trace "Result is not a promise ...")
         (deliver wrapped-data maybe-promise)
         wrapped-data)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Concept Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti get (fn [concept-type & _]
  (log/trace "Dispatching on concept type:" concept-type)
  concept-type))

(defmethod get :collection
  [_type system search-endpoint user-token params]
  (let [cache-key (concept-key (:collection-id params))]
    (get-cached system
                cache-key
                collection/async-get-metadata
                [search-endpoint user-token params])))

(defmethod get :granules
  [_type system search-endpoint user-token params sa-header]
  (let [collection (:collection-id params)
        granules (:granules params)
        expllicit-cache-keys (map #(concept-key (str collection ":" %))
                                  granules)
        implicit-cache-keys [(concept-key (str collection ":granules"))]]
    (get-cached system
                (if (seq granules) expllicit-cache-keys implicit-cache-keys)
                granule/async-get-metadata
                [search-endpoint user-token params sa-header]
                {:multi-key? true})))

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
