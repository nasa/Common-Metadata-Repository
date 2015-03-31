(ns cmr.umm.validation.granule
  "Defines validations for UMM granules"
  (:require [clj-time.core :as t]
            [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.validations.core :as v]
            [cmr.common.util :as util]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.start-end-date :as sed]
            [cmr.spatial.validation :as sv]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.validation.validation-helper :as h]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab.core :as csk]
            [cmr.umm.validation.product-specific-attribute :as psa]))


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
  [field-path spatial-coverage-ref]
  (let [granule-spatial-representation 
        (get-in spatial-coverage-ref [:parent :granule-spatial-representation] :no-spatial)
        is-not-allowed (spatial-field-not-allowed field-path 
                                                  spatial-coverage-ref 
                                                  granule-spatial-representation)
        is-required (spatial-field-is-required field-path 
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
        spatial-representation (get-in spatial-coverage [:parent :granule-spatial-representation])]
    (assoc spatial-coverage
           :geometries
           (map #(umm-s/set-coordinate-system spatial-representation %) geometries))))

(def spatial-coverage-validations
  "Defines spatial coverage validations for granules"
  [spatial-matches-granule-spatial-representation
   (v/pre-validation
    ;; The spatial representation has to be set on the geometries before the conversion because
    ;; polygons etc do not know whether they are geodetic or not.
    set-geometries-spatial-representation
    {:geometries (v/every sv/spatial-validation)})])

(defn- projects-reference-collection
  "Validate projects in granule must reference those in the parent collection"
  [_ granule]
  (let [project-ref-names (set (:project-refs granule))
        parent-project-names (->> granule :parent :projects (map :short-name) set)
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
          parent-value (get-in collection-ref (concat [:parent] parent-field-path))
          field-name (v/humanize-field field)]
      (when (and value (not= value parent-value))
        {[:collection-ref]
         [(format "%%s %s [%s] does not match the %s of the parent collection [%s]"
                  field-name value field-name parent-value)]}))))

(defn- collection-ref-required-fields-validation
  "Validates the granules collection ref has the required fields."
  [_ collection-ref]
  (let [{:keys [short-name version-id entry-title]} collection-ref]
    (when-not (or entry-title (and short-name version-id))
      {[:collection-ref]
       ["%s should have at least Entry Title or Short Name and Version Id."]})))

(defn- temporal-error-message
  "Returns an error message for given pairs of granule and collection start and end dates."
  [gran-start gran-end coll-start coll-end]
  ;; Anything other than this should result in an error:
  ;; timeline: ---coll-start---gran-start---gran-end---coll-end--->
  ;; with no granule end date: ---coll-start---gran-start---coll-end--->
  (cond
    (t/before? gran-start coll-start)
    (format "Granule start date [%s] is earlier than collection start date [%s]."
            gran-start coll-start)

    (t/after? gran-start coll-end)
    (format "Granule start date [%s] is later than collection end date [%s]."
            gran-start coll-end)

    (and gran-end (t/after? gran-end coll-end))
    (format "Granule end date [%s] is later than collection end date [%s]."
            gran-end coll-end)

    (t/after? gran-start gran-end)
    (format "Granule start date [%s] is later than granule end date [%s]."
            gran-start gran-end)))

(defn temporal-validation
  "Checks the granule's temporal extent against the parent collection's."
  [_ temporal]
  (when-let [coll-temporal (:parent temporal)]
    (when-let [msg (temporal-error-message (sed/start-date :granule temporal)
                                           (sed/end-date :granule temporal)
                                           (sed/start-date :collection coll-temporal)
                                           (sed/end-date :collection coll-temporal))]
      {[:temporal] [msg]})))

(defn- operation-modes-reference-collection
  "Validate operation modes in granule instrument ref must reference those in the parent collection"
  [field-path instrument-ref]
  (let [operation-modes (set (:operation-modes instrument-ref))
        parent-operation-modes (-> instrument-ref :parent :operation-modes set)
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
                     (matches-collection-identifier-validation :entry-title [:entry-title])
                     (matches-collection-identifier-validation :short-name [:product :short-name])
                     (matches-collection-identifier-validation :version-id [:product :version-id])]
    :spatial-coverage spatial-coverage-validations
    :temporal temporal-validation
    :platform-refs [(vu/unique-by-name-validator :short-name)
                    (vu/has-parent-validator :short-name "Platform short name")
                    (v/every platform-ref-validations)]
    :product-specific-attributes [(vu/has-parent-validator :name "Product Specific Attribute")
                                  (v/every psa/psa-ref-validations)]
    :project-refs (vu/unique-by-name-validator identity)
    :related-urls h/online-access-urls-validation}
   projects-reference-collection])


