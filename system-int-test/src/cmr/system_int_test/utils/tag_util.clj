(ns cmr.system-int-test.utils.tag-util
  "This contains utilities for testing tagging"
  (:require [cmr.transmit.tag :as tt]
            [cmr.system-int-test.system :as s]))

(defn create-tag
  "Creates a tag."
  [tag]
  (let [{:keys [status body]} (tt/create-tag (s/context) tag {:is-raw? true})]
    (assoc body :status status)))

(comment
  (create-tag {:namespace "foo" :value "v"})

  )