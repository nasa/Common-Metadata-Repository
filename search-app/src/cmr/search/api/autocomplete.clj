(ns cmr.search.api.autocomplete
  "Defines the API for autocomplete-suggest in the CMR."
  (:require
    [clojure.string :as str]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.services.errors :as svc-errors]
    [cmr.search.services.autocomplete-service :as ac]
    [cmr.search.services.query-service :as qs]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.common.mime-types :as mt]
    [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(def limit-default 20)
(def offset-default 0)

(defn- string-param-or-default->int
  [s default]
  (if s
    (Integer/parseInt s 10)
    default))

(defn- get-autocomplete-suggestions
  "Invokes Elasticsearch for autocomplete query result"
  [ctx params]
  (let [query-param (:q params)
        type-param (:types params)]
    (if query-param
      (let [term (str/trim (str/lower-case query-param))
            types (if type-param
                    (drop-while empty?
                                (map str/lower-case (map str/trim (str/split type-param #",")))))
            opts (assoc params :types types
                               :page-size (string-param-or-default->int (:limit params) limit-default)
                               :offset (string-param-or-default->int (:offset params) offset-default))
            results (ac/autocomplete ctx term opts)]
        {:status 200
         :headers {common-routes/CONTENT_TYPE_HEADER (str (mt/format->mime-type :json) "; charset=utf-8")
                   common-routes/CORS_ORIGIN_HEADER  "*"}
         :body {:query {:query term
                        :types (if types types [])
                        :limit (:page-size opts)
                        :offset (:offset opts)}
                :results results}})
      (svc-errors/throw-service-errors
       :bad-request
       ["Missing param [q]"
        "Usage [/autocomplete?q=<term>[&types=[&limit=[&offset=]]]]"
        "q : query string to search for suggestions"
        "types : comma separated list of types"
        "limit : maximum number of suggestions : default 20"
        "offset : offset of results : default 0"]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def autocomplete-api-routes
  (context "/autocomplete" []
           (GET "/"
                {params :params ctx :request-context}
                (get-autocomplete-suggestions ctx params))))
