(ns cmr.common-app.services.ingest.subscription-common
  "This contains the code for the scheduled subscription code to be shared
   between metadata-db and ingest."
  (:require
   [clojure.string :as string]
   [digest :as digest]))

(defn normalize-parameters
  "Returns a normalized url parameter string by splitting the string of parameters on '&' and
   sorting them alphabetically"
  [parameter-string]
  (when parameter-string
    (-> (if (string/starts-with? parameter-string "?")
          (subs parameter-string 1)
          parameter-string)
        (string/split #"&")
        sort
        (as-> $ (string/join "&" $))
        string/trim
        digest/md5)))

(defn create-query-params
  "Create query parameters using the query string like
  \"polygon=1,2,3&concept-id=G1-PROV1\""
  [query-string]
  (let [query-string-list (string/split query-string #"&")
        query-map-list (map #(let [a (string/split % #"=")]
                               {(first a) (second a)})
                             query-string-list)]
     (apply merge query-map-list)))
