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
            [cmr.search.data.elastic-search-index :as es]))

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


(defn- build-routes [system]
  (routes
    ;; For debugging. Gets the state of the world in relations to ACLs and what's indexed
    (GET "/acl-state" []
      {:status 200
       :body (json/generate-string (get-acl-state system) {:pretty true})
       :headers {"Content-Type" "application/json"}})
    (POST "/stop" []
      ((var-get (find-var 'cmr.dev-system.system/stop)) system)
      (System/exit 0))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))



