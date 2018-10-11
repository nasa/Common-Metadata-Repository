(ns cmr.umm-spec.validation.granule
  "Defines validations for UMM granules"
  (:require [clj-time.core :as t]
            [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.validations.core :as v]
            [cmr.common.util :as util]
            [cmr.umm.umm-spatial :as umm-s]
            [cmr.umm.start-end-date :as sed]
            [cmr.umm-spec.time :as umm-spec-time]
            [cmr.spatial.validation :as sv]
            [cmr.umm.validation.validation-utils :as vu]
            [cmr.umm.validation.validation-helper :as h]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.collection.entry-id :as eid]
            [cmr.umm-spec.validation.additional-attribute :as aav]))

(defn- spatial-extent->granule-spatial-representation
  "Returns the granule spatial representation given a parent collection spatial extent"
  [sp]
  (if-let [granule-spatial-representation (:GranuleSpatialRepresentation sp)]
    (csk/->kebab-case-keyword granule-spatial-representation)
    :no-spatial))

(defn- collection->granule-spatial-representation
  "Returns the granule spatial representation given a parent collection"
  [coll]
  (spatial-extent->granule-spatial-representation (:SpatialExtent coll)))

(defn- spatial-field-not-allowed
  "Create a function which takes in :orbit or :geometries as input and returns an error if the field exists"
  [spatial-coverage-path spatial-coverage-ref granule-spatial-representation]
  (fn [field]
    (when (field spatial-coverage-ref)
      {(conj spatial-coverage-path field)
       [(format
          "[%%s] cannot be set when the parent collection's GranuleSpatialRepresentation is %s"
          (str/upper-case (csk/->SCREAMING_SNAKE_CASE (name granule-spatial-representation))))]})))

(defn- spatial-field-is-required
  [spatial-coverage-path spatial-coverage-ref granule-spatial-representation]
  "Create a function which takes in :orbit or :geometries as input and returns an error if the field does not exist"
  (fn [field]
    (when-not (field spatial-coverage-ref)
      {(conj spatial-coverage-path field)
       [(format
          "[%%s] must be provided when the parent collection's GranuleSpatialRepresentation is %s"
          (str/upper-case (csk/->SCREAMING_SNAKE_CASE (name granule-spatial-representation))))]})))

(defn spatial-matches-granule-spatial-representation
  "Validates the consistency of granule's spatial information with the granule spatial representation present in its collection."
  [_ granule]
  (let [granule-spatial-representation (collection->granule-spatial-representation (:parent granule))
        spatial-coverage-ref (:spatial-coverage granule)
        is-not-allowed (spatial-field-not-allowed [:spatial-coverage]
                                                  spatial-coverage-ref
                                                  granule-spatial-representation)
        is-required (spatial-field-is-required [:spatial-coverage]
                                               spatial-coverage-ref
                                               granule-spatial-representation)
        errors (case granule-spatial-representation
                 :no-spatial [(is-not-allowed :orbit)
                              (is-not-allowed :geometries)]
                 (:geodetic :cartesian) [(is-required :geometries)
                                         (is-not-allowed :orbit)]
                 :orbit [(is-not-allowed :geometries)
                         (is-required :orbit)])]
    (apply merge (remove nil? errors))))

(defn set-geometries-spatial-representation
  "Sets the spatial represention from the spatial coverage on the geometries"
  [spatial-coverage]
  (let [{:keys [geometries]} spatial-coverage
        spatial-representation (spatial-extent->granule-spatial-representation (:parent spatial-coverage))]
    (assoc spatial-coverage
           :geometries
           ;; If the granule spatial representation is :no-spatial, then just ignore the geometries.
           (when (and spatial-representation
                      (not= :no-spatial spatial-representation))
             (map #(umm-s/set-coordinate-system spatial-representation %)
                  geometries)))))

(def spatial-coverage-validations
  "Defines spatial coverage validations for granules"
  [(v/pre-validation
     ;; The spatial representation has to be set on the geometries before the conversion because
     ;; polygons etc do not know whether they are geodetic or not.
     set-geometries-spatial-representation
     {:geometries (v/every sv/spatial-validation)
      :orbit (v/when-present sv/spatial-validation)})])

(def ocsd-validations
  "Defines orbit calculated spatial domain validations for granules."
  [(v/every sv/spatial-validation)])

(defn- within-range?
  "Checks if value falls within the closed bounds defined by min-value and max-value. One or both of
  min-value and max-value could be nil in which case the bound will not be checked i.e value will
  be considered within range with respect to the bound."
  [min-val max-val value]
  (and (or (nil? min-val) (>= value min-val))
       (or (nil? max-val) (<= value max-val))))

(defn- validate-coordinate-within-range
  "Function which takes the two-d-coordinate-system of a granule and its path and returns a function
  which takes one of the four 2D coordinate keys defined for a granule in TwoDCoordinateSystem
  and a range of values and validates that the value corresponding to the key falls within the
  range. If the coordinate key is not present in two-d-coordinate-system, it will not be validated."
  [field-path two-d-coordinate-system]
  (fn [coordinate-key min-val max-val]
    (when-let [value (coordinate-key two-d-coordinate-system)]
      (when-not (within-range? min-val max-val value)
        {(conj field-path coordinate-key)
         [(format "The field [%%s] falls outside the bounds [%s %s] defined in the collection"
                  (or min-val "-∞") (or max-val "∞"))]}))))

(defn two-d-coordinates-range-validation
  "Validate that the 2D coordinates in the granule fall within the bounds defined in the collection"
  [field-path two-d-coordinate-system]
  (let [{{min-1 :MinimumValue max-1 :MaximumValue} :Coordinate1
         {min-2 :MinimumValue max-2 :MaximumValue} :Coordinate2} (:parent two-d-coordinate-system)
        check-range  (validate-coordinate-within-range field-path two-d-coordinate-system)]
    (merge
      (check-range :start-coordinate-1  min-1 max-1)
      (check-range :end-coordinate-1  min-1 max-1)
      (check-range :start-coordinate-2  min-2 max-2)
      (check-range :end-coordinate-2  min-2 max-2))))

(defn- projects-reference-collection
  "Validate projects in granule must reference those in the parent collection"
  [_ granule]
  (let [project-ref-names (set (map str/lower-case (:project-refs granule)))
        parent-project-names (->> granule :parent :Projects (map :ShortName) (map str/lower-case) set)
        missing-project-refs (seq (set/difference project-ref-names parent-project-names))]
    (when missing-project-refs
      {[:project-refs]
       [(format "%%s have [%s] which do not reference any projects in parent collection."
                (str/join ", " missing-project-refs))]})))

(defn- matches-collection-identifier-validation
  "Validates the granule collection-ref field matches the corresponding field in the parent collection."
  [field parent-field-path]
  (fn [_ collection-ref]
    (let [value (field collection-ref)
          parent-value (if (= :entry-id field)
                         (eid/umm->entry-id (:parent collection-ref))
                         (get-in collection-ref (concat [:parent] parent-field-path)))
          field-name (v/humanize-field field)]
      (when (and value (not= value parent-value))
        {[:collection-ref]
         [(format "%%s %s [%s] does not match the %s of the parent collection [%s]"
                  field-name value field-name parent-value)]}))))

(defn- collection-ref-required-fields-validation
  "Validates the granules collection ref has the required fields."
  [_ collection-ref]
  (let [{:keys [short-name version-id entry-title entry-id]} collection-ref]
    (when-not (or entry-title entry-id (and short-name version-id))
      {[:collection-ref]
       ["%s should have at least Entry Id, Entry Title or Short Name and Version Id."]})))

(defn- temporal-error-message
  "Returns an error message for given pairs of granule and collection start and end dates."
  [gran-start gran-end coll-start coll-end]
  ;; Anything other than this should result in an error:
  ;; timeline: ---coll-start---gran-start---gran-end---coll-end--->
  ;; with no granule end date: ---coll-start---gran-start---coll-end--->
  ;; NOTE: nil values for gran-end or :present for coll-end are considered to be infinitely
  ;; far in the future
  (cond
    (t/before? gran-start coll-start)
    (format "Granule start date [%s] is earlier than collection start date [%s]."
            gran-start coll-start)

    (and (not= coll-end :present) (t/after? gran-start coll-end))
    (format "Granule start date [%s] is later than collection end date [%s]."
            gran-start coll-end)

    (and (not= coll-end :present) gran-end (t/after? gran-end coll-end))
    (format "Granule end date [%s] is later than collection end date [%s]."
            gran-end coll-end)

    (and (not= coll-end :present) (nil? gran-end))
    (format "There is no granule end date whereas collection has an end date of [%s]" coll-end)

    (and gran-end (t/after? gran-start gran-end))
    (format "Granule start date [%s] is later than granule end date [%s]."
            gran-start gran-end)))

(defn temporal-validation
  "Checks the granule's temporal extent against the parent collection's."
  [_ temporal]
  (if-let [coll-temporals (seq (:parent temporal))]
    (when-let [msg (temporal-error-message (sed/start-date :granule temporal)
                                           (sed/end-date :granule temporal)
                                           (umm-spec-time/collection-start-date {:TemporalExtents coll-temporals})
                                           (umm-spec-time/collection-end-date {:TemporalExtents coll-temporals}))]
      {[:temporal] [msg]})
    (when (some? temporal)
      {[:temporal] ["Granule whose parent collection does not have temporal information cannot have temporal."]})))

(defn- operation-modes-reference-collection
  "Validate operation modes in granule instrument ref must reference those in the parent collection"
  [field-path instrument-ref]
  (let [operation-modes (set (:operation-modes instrument-ref))
        parent-operation-modes (-> instrument-ref :parent :OperationalModes set)
        missing-operation-modes (seq (set/difference operation-modes parent-operation-modes))]
    (when missing-operation-modes
      {field-path
       [(format "The following list of Instrument operation modes did not exist in the referenced parent collection: [%s]."
                (str/join ", " missing-operation-modes))]})))

(def sensor-ref-validations
  "Defines the sensor validations for granules"
  {:characteristic-refs [(vu/unique-by-name-validator :name)
                         (vu/has-parent-validator :name "Characteristic Reference name")]})

(def instrument-ref-validations
  "Defines the instrument validations for granules"
  [{:characteristic-refs [(vu/unique-by-name-validator :name)
                          (vu/has-parent-validator :name "Characteristic Reference name")]
    :sensor-refs [(vu/unique-by-name-validator :short-name)
                  (vu/has-parent-validator :short-name "Sensor short name")
                  (v/every sensor-ref-validations)]}
   operation-modes-reference-collection])

(def platform-ref-validations
  "Defines the platform validations for granules"
  {:instrument-refs [(vu/unique-by-name-validator :short-name)
                     (vu/has-parent-validator :short-name "Instrument short name")
                     (v/every instrument-ref-validations)]})

(def granule-validations
  "Defines validations for granules"
  [{:collection-ref [collection-ref-required-fields-validation
                     (matches-collection-identifier-validation :entry-title [:EntryTitle])
                     (matches-collection-identifier-validation :entry-id [:entry-id])
                     (matches-collection-identifier-validation :short-name [:ShortName])
                     (matches-collection-identifier-validation :version-id [:Version])]
    :spatial-coverage spatial-coverage-validations
    :orbit-calculated-spatial-domains ocsd-validations 
    :temporal temporal-validation
    :platform-refs [(vu/unique-by-name-validator :short-name)
                    (vu/has-parent-validator :short-name "Platform short name")
                    (v/every platform-ref-validations)]
    :two-d-coordinate-system [(vu/has-parent-validator :name "Tiling Identification System Name")
                              two-d-coordinates-range-validation]
    :product-specific-attributes [(vu/has-parent-validator :name "Additional Attribute")
                                  (v/every aav/aa-ref-validations)]
    :project-refs (vu/unique-by-name-validator identity)
    :related-urls h/online-access-urls-validation}
   projects-reference-collection
   spatial-matches-granule-spatial-representation])
