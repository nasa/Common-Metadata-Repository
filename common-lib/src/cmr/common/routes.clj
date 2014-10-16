(ns cmr.common.routes
  "Provides common routes."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [ring.util.response :as r]
            [ring.util.request :as request]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.cache :as cache]
            [cmr.acl.core :as acl]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)
            caches (map name (keys (get-in context [:system :caches])))]
        (acl/verify-ingest-management-permission context :read)
        {:status 200
         :body (json/generate-string caches)}))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)
            cache (cache/context->cache context (keyword cache-name))]
        (acl/verify-ingest-management-permission context :read)
        (when cache
          (let [result (cache/cache-keys cache)]
            (println "RESULT....")
            (println result)
            {:status 200
             :body (json/generate-string result)}))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (let [cache-key (keyword cache-key)
            context (acl/add-authentication-to-context request-context params headers)
            cache (cache/context->cache context (keyword cache-name))
            result (cache/cache-lookup cache cache-key)]
        (acl/verify-ingest-management-permission context :read)
        (when result
          {:status 200
           :body (pr-str result)})))))