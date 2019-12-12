(ns cmr.dev-system.control
  "A namespace that creates a web server for control of the dev system. It allows the system to be
  stopped for easy testing in CI."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common-app.test.side-api :as side-api]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.time-keeper :as tk]
   [cmr.elastic-utils.connect :as elastic-conn]
   [cmr.ingest.api.translation :as ingest-translation-api]
   [cmr.message-queue.test.queue-broker-side-api :as queue-broker-side-api]
   [cmr.search.data.elastic-search-index :as es]
   [cmr.transmit.echo.acls :as echo-acls]
   [compojure.core :refer :all]
   [compojure.route :as route]

   ;; Services for reseting
   [cmr.access-control.api.routes :as access-control]
   [cmr.common.cache :as cache]
   [cmr.indexer.services.index-service :as indexer-service]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.metadata-db.services.concept-service :as mdb-service]
   [cmr.mock-echo.api.routes :as mock-echo-api]
   [cmr.redis-utils.redis :as redis]
   [cmr.search.services.query-service :as search-service]))

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
   (apply (var-get (find-var (symbol (str "cmr.dev-system.system/" function-str)))) args)))

(defn get-acl-state
  [system]
  (let [indexer-cached-acls (deref (get-in system [:apps :indexer :caches :acls]))
        search-cached-acls (deref (get-in system [:apps :search :caches :acls]))
        actual-acls (echo-acls/get-acls-by-types (app-context system :indexer) [:catalog-item])
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
   :indexer indexer-service/reset
   :ingest ingest-service/reset
   :search cache/reset-caches
   :mock-echo mock-echo-api/reset
   :access-control access-control/reset})

(def service-clear-cache-fns
  "A map of services to clear cache functions."
  {:indexer cache/reset-caches
   :access-control cache/reset-caches
   :metadata-db cache/reset-caches
   :search cache/reset-caches
   :ingest cache/reset-caches})

(defn- build-routes [system]
  (routes
    ;; Allow random metadata retrieval
    ingest-translation-api/random-metadata-routes

    ;; Allow code eval
    side-api/eval-routes

    ;; Retrieve KMS resources
    (GET "/kms/:filename" [filename]
      (let [resource (io/resource (str "kms_examples/" filename))]
        (if resource
          {:status 200
           :body (slurp resource)
           :headers {"Content-Type" "application/csv; charset=utf-8"}}
          (route/not-found "KMS resource not found\n"))))

    ;; For debugging. Gets the state of the world in relations to ACLs and what's indexed
    (GET "/acl-state" []
      {:status 200
       :body (json/generate-string (get-acl-state system) {:pretty true})
       :headers {"Content-Type" "application/json"}})

    ;; Calls reset on all other systems internally
    (POST "/reset" []
      (debug "dev system /reset")
      (redis/reset)
      (doseq [[service-name reset-fn] service-reset-fns
              ;; Only call reset on applications which are deployed to the current system
              :when (get-in system [:apps service-name])]
        (reset-fn (app-context system service-name)))
      ;; After reset some elasticsearch indexes may not be initialized yet. We will check the status here
      (elastic-conn/wait-for-healthy-elastic (get-in system [:apps :indexer :db]))
      (debug "dev system /reset complete")
      {:status 200})

    (GET "/component-types" []
      (debug "Retrieving component types")
      {:status 200
       :body (json/generate-string (exec-dev-system-function "component-type-map"))
       :headers {"Content-Type" "application/json"}})

    (POST "/clear-cache" []
      (debug "dev system /clear-cache")
      (redis/reset)
      (doseq [[service-name clear-cache-fn] service-clear-cache-fns]
        (clear-cache-fn (app-context system service-name)))
      (debug "dev system /clear-cache complete")
      {:status 200})

    (POST "/stop" []
      (debug "dev system /stop")
      (exec-dev-system-function "stop" system)
      (System/exit 0))

    ;; Defines the time keeper API that allows programmatic HTTP control of the time of the CMR
    ;; running in dev-system.
    (context "/time-keeper" []
      (POST "/clear-current-time" []
        (tk/clear-current-time!)
        {:status 200})
      (POST "/freeze-time" []
        (tk/freeze-time!)
        {:status 200})
      (PUT "/freeze-time/:date-time" [date-time]
        (tk/set-time-override! (parser/parse-datetime date-time))
        {:status 200})
      (POST "/advance-time/:num-secs" [^String num-secs]
        (tk/advance-time! (Long. num-secs))
        {:status 200}))

    (queue-broker-side-api/build-routes (get-in system [:pre-components :broker-wrapper]))))


(defn create-server
  []
  (side-api/create-side-server build-routes))
