(ns cmr.search.api.autocomplete
  "Defines the API for autocomplete-suggest in the CMR."
  (:require
    [clojure.string :as string]
    [compojure.core :refer :all]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as svc-errors]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.search.api.core :as core-api]
    [cmr.search.services.autocomplete-service :as ac]
    [cmr.search.services.query-service :as qs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(def page-size-default 10)
(def page-num-default 0)

(defn- string-param-or-default->int
  [s default]
  (if s
    (Integer/parseInt s)
    default))

(defn- lower-case-and-trim
  [s]
  (string/lower-case (string/trim s)))

(defn- process-autocomplete-query
  "Process and autocomplete and return value"
  [ctx query types params]
  (let [term (lower-case-and-trim query)
        types (when types
                (filter #(not (empty? %))
                        (map lower-case-and-trim (string/split types #","))))
        page-size (string-param-or-default->int (:page-size params) page-size-default)
        offset (if (:page-num params)
                 (* page-size (string-param-or-default->int (:page-num params) page-num-default))
                 (if (:offset params)
                   (string-param-or-default->int (:offset params) page-num-default)
                   page-num-default))
        opts (assoc params :types types
                           :page-size page-size
                           :offset offset)
        results (ac/autocomplete ctx term opts)]
    {:status 200
     :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)
               common-routes/CORS_ORIGIN_HEADER  "*"}
     :body {:query {:query term
                    :types (if types types [])}
            :results results}}))

(defn- get-autocomplete-suggestions-handler
  "Validate params and invoke autocomplete"
  [ctx path-w-extension params headers]
  (let [opts (core-api/process-params :autocomplete params path-w-extension headers mt/json)
        query (:q opts)
        types (:types opts)]
    (when-not query
      (svc-errors/throw-service-errors
       :bad-request
       ["Missing param [q]"
        "Usage [/autocomplete?q=<term>[&types=[&page-size=[&page-number=]]]]"
        "q : query string to search for suggestions"
        "types : comma separated list of types"
        "offset : maximum number of suggestions : default 0"
        "page_size : results page : default 20"]))
    (process-autocomplete-query ctx query types opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def autocomplete-api-routes
  (context ["/:path-w-extension" :path-w-extension #"(?:autocomplete)(?:\..+)?"] [path-w-extension]
    (GET "/" {params :params headers :headers ctx :request-context}
      (get-autocomplete-suggestions-handler ctx path-w-extension params headers))))
