(ns cmr.metadata-db.api.services
  (:require [cmr.metadata-db.data :as data]))

(defn create-concept
  "Store a concept record and return the revision"
  [{:keys [db]} concept]
  (let [revision (data/insert-concept db concept)]
    {:status 201
     :body {:revision-id revision}
     :headers {"Content-Type" "json"}}))