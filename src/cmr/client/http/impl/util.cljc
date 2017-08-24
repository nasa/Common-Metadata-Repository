(ns cmr.client.http.impl.util
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]))

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
     :json (json/read-str (:body response) :key-fn keyword)
     :unsupported (:body response)))

(defn parse-body!
  ([response]
   (parse-body! response (parse-content-type response)))
  ([response content-type]
   (assoc response :body (convert-body response content-type))))
