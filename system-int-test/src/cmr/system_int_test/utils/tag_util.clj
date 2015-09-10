(ns cmr.system-int-test.utils.tag-util
  "This contains utilities for testing tagging"
  (:require [cmr.transmit.tag :as tt]
            [cmr.system-int-test.system :as s]))

(defn make-tag
  "Makes a valid unique tag"
  [n]
  {:namespace "org.nasa.something"
   :category "QA"
   :value (str "value" n)
   :description "A very good tag"})

(defn create-tag
  "Creates a tag."
  [token tag]
  (let [{:keys [status body]} (tt/create-tag (s/context) tag {:is-raw? true :token token})]
    (if (map? body)
      (assoc body :status status)
      {:status status
       :body body})))
