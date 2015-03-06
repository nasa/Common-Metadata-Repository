(ns cmr.dev-system.control
  "A namespace that creates a web server for control of the dev system. It allows the system to be
  stopped for easy testing in CI."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.search.data.elastic-search-index :as es]
            [cmr.dev-system.queue-broker-wrapper :as wrapper]
            [cmr.ingest.config :as iconfig]

            ;; Services for reseting
            [cmr.metadata-db.services.concept-service :as mdb-service]
            [cmr.index-set.services.index-service :as index-set-service]
            [cmr.indexer.services.index-service :as indexer-service]
            [cmr.ingest.services.ingest :as ingest-service]
            [cmr.search.services.query-service :as search-service]
            [cmr.mock-echo.api.routes :as mock-echo-api]
            [cmr.common.cache :as cache]))

(defn app-context
  [system app]
  {:system (get-in system [:apps app])})

(defn exec-dev-system-function
  "Executes a function from the cmr.dev-system.system namespace. Takes a string containing the name
  of the function to run. Need to look up at run-time due to a circular dependency.

  Example: (exec-dev-system-function \"stop\" system ) calls (cmr.dev-system.system/stop system)"
  ([function-str]
   ((var-get (find-var (symbol (str "cmr.dev-system.system/" function-str))))))
  ([function-str & args]
   ((var-get (find-var (symbol (str "cmr.dev-system.system/" function-str)))) args)))

(defn get-acl-state
  [system]
  (let [indexer-cached-acls (deref (get-in system [:apps :indexer :caches :acls]))
        search-cached-acls (deref (get-in system [:apps :search :caches :acls]))
        actual-acls (echo-acls/get-acls-by-type (app-context system :indexer) "CATALOG_ITEM")
        coll-permitted-groups (es/get-collection-permitted-groups (app-context system :search))
        result {:collection-permitted-groups coll-permitted-groups}]

    (merge result
           (if (= (:acls indexer-cached-acls) actual-acls)
             {:indexer-cached-acls-current true}
             {:indexer-cached-acls-current false
              :indexer-cached-acls indexer-cached-acls
              :actual-acls actual-acls})
           (if (= (:acls search-cached-acls) actual-acls)
             {:search-cached-acls-current true}
             {:search-cached-acls-current false
              :search-cached-acls search-cached-acls
              :actual-acls actual-acls}))))

(def service-reset-fns
  "A map of services to reset functions."
  {:metadata-db mdb-service/reset
   ;; The index set app is not reset as part of this because the indexer will handle it.
   :indexer indexer-service/reset
   :ingest ingest-service/reset
   :search cache/reset-caches
   :mock-echo mock-echo-api/reset})

(def service-clear-cache-fns
  "A map of services to reset functions."
  {:indexer cache/reset-caches
   :index-set cache/reset-caches
   :metadata-db cache/reset-caches
   :search cache/reset-caches
   :ingest cache/reset-caches})

(defn- build-routes [system]
  (routes
    ;; For debugging. Gets the state of the world in relations to ACLs and what's indexed
    (GET "/acl-state" []
      {:status 200
       :body (json/generate-string (get-acl-state system) {:pretty true})
       :headers {"Content-Type" "application/json"}})

    ;; Calls reset on all other systems internally
    (POST "/reset" []
      (debug "dev system /reset")
      (doseq [[service-name reset-fn] service-reset-fns
              ;; Only call reset on applications which are deployed to the current system
              :when (get-in system [:apps service-name])]
        (reset-fn (app-context system service-name)))
      (debug "dev system /reset complete")
      {:status 200})

    (GET "/component-types" []
      (debug "Retrieving component types")
      {:status 200
       :body (json/generate-string (exec-dev-system-function "component-type-map"))
       :headers {"Content-Type" "application/json"}})

    (POST "/clear-cache" []
      (debug "dev system /clear-cache")
      (doseq [[service-name clear-cache-fn] service-clear-cache-fns]
        (clear-cache-fn (app-context system service-name)))
      (debug "dev system /clear-cache complete")
      {:status 200})

    (POST "/stop" []
      (debug "dev system /stop")
      (exec-dev-system-function "stop" system)
      (System/exit 0))

    (context "/message-queue" []
      (POST "/wait-for-indexing" []
        (let [broker-wrapper (get-in system [:pre-components :broker-wrapper])]
          (debug "dev system /wait-for-indexing")
          (when (iconfig/use-index-queue?)
            (wrapper/wait-for-indexing broker-wrapper))
          (debug "indexing complete")
          {:status 200}))

      (GET "/history" []
        (let [broker-wrapper (get-in system [:pre-components :broker-wrapper])]
          {:status 200
           :body (wrapper/get-message-queue-history broker-wrapper)
           :headers {"Content-Type" "application/json"}}))

      ;; TODO
      ;; All messages return failures
      #_(POST "/failure-mode" []
          (debug "dev system setting message queue to failure mode.")
          (when (iconfig/use-index-queue?)
            (let [broker-wrapper (get-in system [:pre-components :broker-wrapper])]
              (wrapper/set-message-mode :failure)))
          {:status 200})

      ;; Messages are processed normally
      #_(POST "/normal-mode" []
          (debug "dev system returning message queue to normal mode.")
          (when (iconfig/use-index-queue?)
            (let [broker-wrapper (get-in system [:pre-components :broker-wrapper])]
              (wrapper/set-message-mode :normal)))
          {:status 200}))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))



