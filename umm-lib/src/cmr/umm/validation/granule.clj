(ns cmr.umm.validation.granule
  "Defines validations for UMM granules"
  (:require [clj-time.core :as t]
            [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.validations.core :as v]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.start-end-date :as sed]
            [cmr.spatial.validation :as sv]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.validation.validation-helper :as h]
            [cmr.common.services.errors :as errors]))


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
  (v/pre-validation
    ;; The spatial representation has to be set on the geometries before the conversion because
    ;;polygons etc do not know whether they are geodetic or not.
    set-geometries-spatial-representation
    {:geometries (v/every sv/spatial-validation)}))

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

(defn collection-ref-validation
  "Validates the granules collection ref matches the parent collection."
  [_ granule]
  (let [{{:keys [short-name version-id entry-title]} :collection-ref} granule
        entry-title-err (when entry-title
                          (let [coll-entry-title (get-in granule [:parent :entry-title])]
                            (when-not (= coll-entry-title entry-title)
                              {[:collection-ref]
                               [(format "%%s Entry Title [%s] does not match the entry title of the parent collection [%s]"
                                        entry-title coll-entry-title)]})))
        short-name-version-err (when (and short-name version-id)
                                 (let [{coll-short-name :short-name
                                        coll-version-id :version-id} (get-in granule [:parent :product])]
                                   (when-not (and (= coll-short-name short-name) (= coll-version-id version-id))
                                     {[:collection-ref]
                                      [(format (str "%%s Short Name [%s] and Version ID [%s] do not match the Short Name [%s] "
                                                    "and Version ID [%s] of the parent collection.")
                                               short-name version-id coll-short-name coll-version-id)]})))]

    (when-not (or entry-title (and short-name version-id))
      (errors/internal-error! (str "Unexpected collection ref in granule: " (pr-str granule))))

    (merge-with concat entry-title-err short-name-version-err)))

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
  [collection-ref-validation
   {:spatial-coverage spatial-coverage-validations
    :temporal temporal-validation
    :platform-refs [(vu/unique-by-name-validator :short-name)
                    (vu/has-parent-validator :short-name "Platform short name")
                    (v/every platform-ref-validations)]
    :product-specific-attributes (vu/has-parent-validator :name "Product Specific Attribute")
    :project-refs (vu/unique-by-name-validator identity)
    :related-urls h/online-access-urls-validation}
   projects-reference-collection])


