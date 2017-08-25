(ns cmr.client.http.impl.util
  (:require
   [clojure.string :as string]
   [cmr.client.util :as util]))

(defn get-default-options
  [this]
  (assoc (:options this) :async? true))

(defn parse-content-type
  [response]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (cond
      (string/includes? content-type "json") :json
      :else :unsupported)))

(defn convert-body
  [response content-type]
  (case content-type
     :json (util/read-json-str (:body response))
     :unsupported (:body response)))

(defn parse-body!
  ([response]
   (parse-body! response (parse-content-type response)))
  ([response content-type]
   (assoc response :body (convert-body response content-type))))
