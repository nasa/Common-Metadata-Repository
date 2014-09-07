(ns cmr.search.data.complex-to-simple-converters.spatial
  "Contains converters for spatial condition into the simpler executable conditions"
  (:require [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.models.query :as qm]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.serialize :as srl]
            [cmr.spatial.derived :as d]
            [clojure.string :as str]))


(defn- shape->script-cond
  [shape]
  (let [ords-info-map (-> (srl/shapes->ords-info-map [shape])
                          (update-in [:ords-info] #(str/join "," %))
                          (update-in [:ords] #(str/join "," %)))]
    (qm/map->ScriptCondition {:name "spatial"
                              :params ords-info-map})))

(defn- br->cond
  [prefix {:keys [west north east south] :as br}]
  (letfn [(add-prefix [field]
                      (->> field name (str prefix "-") keyword))
          (range-cond [field from to]
                      (qm/numeric-range-condition (add-prefix field) from to))
          (bool-cond [field value]
                     (qm/->BooleanCondition (add-prefix field) value))]
    (if (mbr/crosses-antimeridian? br)
      (let [c (range-cond :west -180 west)
            d (range-cond :east -180 east)
            e (range-cond :east east 180)
            f (range-cond :west west 180)
            am-conds (qm/and-conds [(qm/or-conds [c f])
                                    (qm/or-conds [d e])
                                    (bool-cond :crosses-antimeridian true)])
            lon-cond (qm/or-conds [(range-cond :west -180 east)
                                   (range-cond :east west 180)
                                   am-conds])]
        (qm/and-conds [lon-cond
                       (range-cond :north south 90)
                       (range-cond :south -90 north)]))

      (let [north-cond (range-cond :north south 90.0)
            south-cond (range-cond :south -90.0 north)
            west-cond (range-cond :west -180 east)
            east-cond (range-cond :east west 180)
            am-conds (qm/and-conds [(bool-cond :crosses-antimeridian true)
                                    (qm/or-conds [west-cond east-cond])])
            non-am-conds (qm/and-conds [west-cond east-cond])]
        (qm/and-conds [north-cond
                       south-cond
                       (qm/or-conds [am-conds non-am-conds])])))))

;; TODO find a way to pass in the rest of the parameters so we can look for collection ids to
;; narrow the search like catalog rest does
;; TODO - we must have a context here so that we can call the service to look up the
;; orbit parameters
(defn orbit-cond
  "Create a condition that will use orbit parameters and orbital back tracking to find matches
  to a spatial search."
  [shape]
  ;; Construct a query for concept-ids and orbit parameters of all collections that have them
  ;; TODO this should be limited to any parent collection ids specified in the paramters (if any)
  ;; Execute the query to get the data needed to do orbitial back tracking


  ;; Use the orbit parameters to perform oribtial back tracking to longitude ranges to be used
  ;; in the search

  ;; Construct an OR group with the ranges, looking for overlaps with the :orbit-start-clat
  ;; and :orbit-end-clat range
  )


(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.SpatialCondition
  (c2s/reduce-query
    [{:keys [shape]} context]
    (let [shape (d/calculate-derived shape)
          mbr-cond (br->cond "mbr" (srl/shape->mbr shape))
          lr-cond (br->cond "lr" (srl/shape->lr shape))
          spatial-script (shape->script-cond shape)]
      (qm/and-conds [mbr-cond (qm/or-conds [lr-cond spatial-script])]))))