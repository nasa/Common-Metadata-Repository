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
