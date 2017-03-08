(ns cmr.umm-spec.migration.spatial-extent-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each]]))

(defn remove-it-now
  "Remove the :CenterPoint"
  [m path]
  (if (not= nil (get-in m path))
    (update-in-each m path #(dissoc % :CenterPoint))
    m))

(defn remove-center-point
  "Remove :CenterPoint from :BoundingRectangles, :GPolyons and :Lines
  to comply with UMM spec v1.9"
  [spatial-extent]
  (remove-it-now
    (remove-it-now
      (remove-it-now spatial-extent [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
      [:HorizontalSpatialDomain :Geometry :GPolygons])
    [:HorizontalSpatialDomain :Geometry :Lines]))
