(ns cmr.umm-spec.migration.spatial-extent-migration
  "Contains helper functions for migrating between different versions of UMM related urls")

(defn remove-centerPoint
  "Remove :CenterPoint from :BoundingRectangles, :GPolyons and :Lines
  to comply with UMM spec v1.9"
  [spatial-extent]
  (clojure.walk/postwalk #(if (map? %) (dissoc % :CenterPoint) %) spatial-extent))
