(ns cmr.umm-spec.validation.parent-weaver
  "Provides functions to thread together a granule and collection parent objects for validation.
  It weaves together the objects so matching items within the granule and collection are combined"
  (:require [cmr.umm.umm-granule :as g]
            [cmr.common.util :as u])
  (:import [cmr.umm.umm_granule
            UmmGranule
            PlatformRef
            InstrumentRef
            SensorRef
            TwoDCoordinateSystem]))

(defprotocol ParentWeaver
  (set-parent [obj parent] "Sets the parent attribute on this object with the given parent"))

(defn- set-parents-by-name
  "Takes a list of child objects and a list of parent objects for the same field.  A parent
  object is matched to a child object by the field passed in as the name-field.  If a match is
  found for the child object in the parent object then the :parent field is set to that match."
  ([objs parent-objs]
   (set-parents-by-name objs parent-objs :name :Name))
  ([objs parent-objs name-field parent-name-field]
   ;; We'll assume there's only a single parent object with a given name
   (let [parent-obj-by-name (u/map-values first (group-by #(u/safe-lowercase (parent-name-field %)) parent-objs))]
     (for [child objs
           :let [parent (parent-obj-by-name (u/safe-lowercase (name-field child)))]]
       (set-parent child parent)))))

(defn- set-parent-by-name
  "This function does the same thing as set-parents-by-name, but for the case where the parent has
  multiple objects but there is only one child object(in granule) i.e. child object is not a list of
  values but a single value with a reference to its parent"
  ([obj parent-objs]
   (set-parent-by-name obj parent-objs :name :Name))
  ([obj parent-objs name-field parent-name-field]
   (let [parent-obj-by-name (u/map-values first (group-by  #(u/safe-lowercase (parent-name-field %)) parent-objs))]
     (set-parent obj (parent-obj-by-name (u/safe-lowercase (name-field obj)))))))

(extend-protocol
  ParentWeaver

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  UmmGranule
  (set-parent
    [granule coll]
    (-> granule
        (assoc :parent coll)
        (update :collection-ref set-parent coll)
        (update :spatial-coverage set-parent (:SpatialExtent coll))
        (update :temporal set-parent (:TemporalExtents coll))
        (update :platform-refs set-parents-by-name (:Platforms coll) :short-name :ShortName)
        (update :two-d-coordinate-system set-parent-by-name
                (:TilingIdentificationSystems coll) :name :TilingIdentificationSystemName)
        (update :product-specific-attributes
                set-parents-by-name (:AdditionalAttributes coll))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  clojure.lang.IPersistentMap
  ;; Default implementation of set-parent for associative arrays
  (set-parent
    [obj parent]
    (assoc obj :parent parent))

  PlatformRef
  (set-parent
    [platform-ref platform]
    (-> platform-ref
        (assoc :parent platform)
        (update-in [:instrument-refs] set-parents-by-name (:Instruments platform) :short-name :ShortName)))

  InstrumentRef
  (set-parent
    [instrument-ref instrument]
    (-> instrument-ref
        (assoc :parent instrument)
        (update-in [:sensor-refs] set-parents-by-name (:ComposedOf instrument) :short-name :ShortName)
        (update-in [:characteristic-refs] set-parents-by-name (:Characteristics instrument))))

  SensorRef
  (set-parent
    [sensor-ref sensor]
    (-> sensor-ref
        (assoc :parent sensor)
        (update-in [:characteristic-refs] set-parents-by-name (:Characteristics sensor))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; The protocol is extended to nil so we can attempt to set the parent on items which do not have
  ;; a parent
  nil
  (set-parent
    [_ _]
    nil))
