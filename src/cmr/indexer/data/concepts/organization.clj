(ns cmr.indexer.data.concepts.organization
  "Contains functions to extract organizaiton fields")

(defn extract-archive-centers
  "Extract the archive organization/archive-centers from collection"
  [collection]
  (let [orgs (:organizations collection)]
    (remove nil? (for [org orgs]
                   (let [{:keys [type org-name]} org]
                     (when (= :archive-center type) org-name))))))