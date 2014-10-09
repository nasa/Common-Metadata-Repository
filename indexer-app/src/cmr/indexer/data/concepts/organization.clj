(ns cmr.indexer.data.concepts.organization
  "Contains functions to extract organizaiton fields")

(defn extract-archive-centers
  "Extract the archive organization/archive-centers from collection"
  [collection]
  (let [orgs (:organizations collection)]
    (for [org orgs
          :when (= :archive-center (:type org))]
      (:org-name org))))