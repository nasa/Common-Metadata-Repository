(ns cmr.search.api.semantic-search
  "Thin, feature-gated proxy for the internal semantic search service."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as string]
   [cmr.search.config :as config]
   [compojure.core :refer [GET]]))

(def allowed-parameters
  #{:q :mode :page_size :temporal :bounding_box})

(defn- error-response [status message]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string {:errors [message]})})

(defn- validate-params [params]
  (let [query (some-> (:q params) string/trim)
        mode (:mode params)
        page-size (:page_size params)]
    (cond
      (string/blank? query) "q is required and must not be blank."
      (> (count query) 1000) "q must not exceed 1000 characters."
      (and mode (not (#{"lexical" "semantic" "hybrid"} mode)))
      "mode must be lexical, semantic, or hybrid."
      (and page-size
           (try
             (not (<= 1 (Long/parseLong page-size) 20))
             (catch NumberFormatException _ true)))
      "page_size must be an integer from 1 through 20."
      :else nil)))

(defn proxy-request
  "Validate and forward a semantic search request. Kept public for focused unit testing."
  [params]
  (if-let [message (validate-params params)]
    (error-response 400 message)
    (try
      (let [response (http/get (str (string/replace (config/semantic-search-url) #"/$" "")
                                    "/semantic-collections")
                               {:query-params (select-keys params allowed-parameters)
                                :accept :json
                                :as :text
                                :throw-exceptions false
                                :connection-timeout (config/semantic-search-timeout-ms)
                                :socket-timeout (config/semantic-search-timeout-ms)})
            status (:status response)
            body (:body response)]
        ;; Parsing prevents an HTML proxy/server error from escaping as a JSON API response.
        (json/parse-string body)
        (if (#{200 400 502 503} status)
          {:status status
           :headers {"Content-Type" "application/json"}
           :body body}
          (error-response 502 "Semantic search service returned an unsupported response.")))
      (catch Exception _
        (error-response 502 "Semantic search service is unavailable.")))))

(def semantic-search-routes
  (GET "/semantic-collections" {params :params}
    (if (config/semantic-search-enabled)
      (proxy-request params)
      (error-response 404 "Semantic collection search is not enabled."))))
