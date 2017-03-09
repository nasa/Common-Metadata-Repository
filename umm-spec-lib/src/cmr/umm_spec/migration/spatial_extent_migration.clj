(ns cmr.umm-spec.migration.spatial-extent-migration
  "Contains helper functions for migrating between different versions of UMM related urls"
  (:require
   [cmr.common.util :refer [update-in-each]]))

(defn dissoc-center-point
  "Remove the :CenterPoint element from the path"
  [m path]
  (if (not= nil (get-in m path))
    (update-in-each m path #(dissoc % :CenterPoint))
    m))

(defn remove-center-point
  "Remove :CenterPoint from :BoundingRectangles, :GPolyons and :Lines
  to comply with UMM spec v1.9"
  [spatial-extent]
  (-> spatial-extent
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :BoundingRectangles])
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :GPolygons])
    (dissoc-center-point [:HorizontalSpatialDomain :Geometry :Lines])))
