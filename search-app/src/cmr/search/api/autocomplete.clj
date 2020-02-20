(ns cmr.search.api.autocomplete
  "Defines the API for autocomplete-suggest in the CMR."
  (:require
    [clojure.string :as str]
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

(def page-size-default 20)
(def page-num-default 0)

(defn- string-param-or-default->int
  [s default]
  (if s
    (Integer/parseInt s 10)
    default))

(defn- lower-case-and-trim
  [s]
  (str/lower-case (str/trim s)))

(defn- get-autocomplete-suggestions
  "Invokes Elasticsearch for autocomplete query result"
  [ctx path-w-extension params headers]
  (let [params (core-api/process-params :autocomplete params path-w-extension headers mt/json)
        query (:q params)
        type-param (:types params)]
    (if query
      (let [term (lower-case-and-trim query)
            types (when type-param
                    (filter #(not (empty? %))
                            (map lower-case-and-trim (str/split type-param #","))))
            page-size (string-param-or-default->int (:page-size params) page-size-default)
            page-num (string-param-or-default->int (:page-num params) page-num-default)
            offset (* page-size page-num)
            opts (assoc params :types types
                        :page-size page-size
                        :offset offset)
            results (ac/autocomplete ctx term opts)]
        {:status 200
         :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)
                   common-routes/CORS_ORIGIN_HEADER  "*"}
         :body {:query {:query term
                        :types (if types types [])
                        :page-size (:page-size opts)
                        :page-num (:offset opts)}
                :results results}})
      ;; Handle the case of no query term
      (svc-errors/throw-service-errors
       :bad-request
       ["Missing param [q]"
        "Usage [/autocomplete?q=<term>[&types=[&page-size=[&page-number=]]]]"
        "q : query string to search for suggestions"
        "types : comma separated list of types"
        "page-size : maximum number of suggestions : default 20"
        "page-number : results page : default 0"]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def autocomplete-api-routes
  (context ["/:path-w-extension" :path-w-extension #"(?:autocomplete)(?:\..+)?"] [path-w-extension]
     (GET "/" {params :params headers :headers ctx :request-context}
        (get-autocomplete-suggestions ctx path-w-extension params headers))))
