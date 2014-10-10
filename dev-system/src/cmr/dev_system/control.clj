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

            ;; Services for reseting
            [cmr.metadata-db.services.concept-service :as mdb-service]
            [cmr.index-set.services.index-service :as index-set-service]
            [cmr.indexer.services.index-service :as indexer-service]
            [cmr.search.services.query-service :as search-service]
            [cmr.mock-echo.api.routes :as mock-echo-api]
            [cmr.common.cache :as cache]))

(defn app-context
  [system app]
  {:system (get-in system [:apps app])})

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
   :index-set index-set-service/reset
   :indexer indexer-service/reset
   :search cache/reset-caches
   :mock-echo mock-echo-api/reset})

(def service-clear-cache-fns
  "A map of services to reset functions."
  {:indexer cache/reset-caches
   :search cache/reset-caches})


(defn- build-routes [system]
  (routes
    ;; For debugging. Gets the state of the world in relations to ACLs and what's indexed
    (GET "/acl-state" []
      {:status 200
       :body (json/generate-string (get-acl-state system) {:pretty true})
       :headers {"Content-Type" "application/json"}})

    ;; Calls reset on all other systems internally
    (POST "/reset" []
      (doseq [[service-name reset-fn] service-reset-fns]
        (reset-fn (app-context system service-name)))
      {:status 200})

    (POST "/clear-cache" []
      (doseq [[service-name clear-cache-fn] service-clear-cache-fns]
        (clear-cache-fn (app-context system service-name)))
      {:status 200})

    (POST "/stop" []
      ((var-get (find-var 'cmr.dev-system.system/stop)) system)
      (System/exit 0))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))



