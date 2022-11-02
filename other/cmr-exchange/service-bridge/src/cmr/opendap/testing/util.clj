(ns cmr.opendap.testing.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.http.kit.request :as request]
   [cmr.ous.util.http.request :as ous-request])
  (:import
   (clojure.lang Keyword)))

(def vendor "cmr-service-bridge.")

(defn override-api-version-header
  ([version]
   (override-api-version-header {} version))
  ([req version]
   (request/add-header req
                       "Accept"
                       (format ous-request/version-format
                               vendor
                               version
                               "json"))))

(defn parse-response
  [response]
  (try
    (let [data (json/parse-string (:body response) true)]
      (cond
        (some? (:items data))
        (:items data)

        :else data))
    (catch Exception e
      {:error {:msg "Couldn't parse body."
               :body (:body response)}})))

(defn create-json-payload
  [data]
  {:body (json/generate-string data)})

(defn create-json-stream-payload
  [data]
  {:body (io/input-stream
          (byte-array
           (map (comp byte int)
            (json/generate-string data))))})

(defn get-env-token
  [^Keyword deployment]
  (System/getenv (format "CMR_%s_TOKEN"
                         (string/upper-case (name deployment)))))

(def get-sit-token #(get-env-token :sit))
(def get-uat-token #(get-env-token :uat))
(def get-prod-token #(get-env-token :prod))
