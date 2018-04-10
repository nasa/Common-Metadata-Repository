(ns cmr.opendap.testing.util
  (:require
   [cheshire.core :as json]
   [clojure.string :as string])
  (:import
   (clojure.lang Keyword)))

(defn parse-response
  [response]
  (try
    (json/parse-string (:body response) true)
    (catch Exception e
      {:error {:msg "Couldn't parse body."
               :body (:body response)}})))

(defn get-env-token
  [^Keyword deployment]
  (System/getenv (format "CMR_%s_TOKEN"
                         (string/upper-case (name deployment)))))

(def get-sit-token #(get-env-token :sit))
(def get-uat-token #(get-env-token :uat))
(def get-prod-token #(get-env-token :prod))
