(ns cmr.system-int-test.data2.collection
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.umm-collection :as c]
            [cmr.common.util :as util]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.collection.temporal :as ct]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [clj-time.format :as f]
            [cmr.umm.collection.entry-id :as eid]
            [cmr.umm.umm-spatial :as umm-s])
  (:import [cmr.umm.umm_collection
            Product
            DataProviderTimestamps
            ScienceKeyword
            UmmCollection]))

(defn psa
  "Creates a product specific attribute."
  [psa]
  (let [{:keys [name group description data-type value min-value max-value]} psa]
    (if (or (some? min-value) (some? max-value))
      (c/map->ProductSpecificAttribute
        {:name name
         :group group
         :description (or description "Generated")
         :data-type data-type
         :parsed-parameter-range-begin min-value
         :parsed-parameter-range-end max-value
         :parameter-range-begin (psa/gen-value data-type min-value)
         :parameter-range-end (psa/gen-value data-type max-value)})
      (c/map->ProductSpecificAttribute
        {:name name
         :group group
         :description (or description "Generated")
         :data-type data-type
         :parsed-value value
         :value (psa/gen-value data-type value)}))))

(defn two-d
  "Creates two-d-coordinate-system specific attribute"
  [name]
  (c/map->TwoDCoordinateSystem
    {:name name}))

(defn two-ds
  "Returns a sequence of two-d-coordinate-systems with the given names"
  [& names]
  (map two-d names))

(defn product
  [attribs]
  (let [attribs (select-keys attribs (util/record-fields Product))
        minimal-product {:short-name (d/unique-str "short-name")
                         :long-name (d/unique-str "long-name")
                         :version-id (d/unique-str "V")}]
    (c/map->Product (merge minimal-product attribs))))

(defn data-provider-timestamps
  [attribs]
  (let [attribs (util/remove-nil-keys
                  (select-keys attribs (util/record-fields DataProviderTimestamps)))
        attribs (into {} (for [[k v] attribs] [k (p/parse-datetime v)]))
        minimal-timestamps {:insert-time (d/make-datetime 10 false)
                            :update-time (d/make-datetime 18 false)
                            :revision-date-time (d/make-datetime 18 false)}]
    (c/map->DataProviderTimestamps (merge minimal-timestamps attribs))))

(defn temporal
  "Return a temporal with range date time of the given date times"
  [attribs]
  (let [{:keys [beginning-date-time ending-date-time single-date-time ends-at-present?]} attribs
        begin (when beginning-date-time (p/parse-datetime beginning-date-time))
        end (when ending-date-time (p/parse-datetime ending-date-time))
        single (when single-date-time (p/parse-datetime single-date-time))]
    (cond
      (or begin end)
      (ct/temporal {:range-date-times [(c/->RangeDateTime begin end)]
                    :ends-at-present-flag ends-at-present?})

      single
      (ct/temporal {:single-date-times [single]}))))

(defn science-keyword
  [attribs]
  (c/map->ScienceKeyword attribs))

(defn sensor
  "Return an sensor based on sensor short-name"
  [attribs]
  (c/map->Sensor (merge {:short-name (d/unique-str "short-name")}
                        attribs)))

(defn sensors
  "Return a sequence of sensors with the given short names"
  [& short-names]
  (map #(c/map->Sensor
          {:short-name %
           :long-name (d/unique-str "long-name")})
       short-names))

(defn instrument
  "Return an instrument based on instrument attribs"
  [attribs]
  (c/map->Instrument (merge {:short-name (d/unique-str "short-name")}
                            attribs)))

(defn instruments
  "Return a sequence of instruments with the given short names"
  [& short-names]
  (map #(c/map->Instrument
          {:short-name %
           :long-name (d/unique-str "long-name")})
       short-names))

(defn instrument-with-sensors
  "Return an instrument, with a sequence of sensors"
  [short-name & sensor-short-names]
  (let [sensors (apply sensors sensor-short-names)]
    (c/map->Instrument {:short-name short-name
                        :long-name (d/unique-str "long-name")
                        :sensors sensors})))

(defn characteristic
  "Returns a platform characteristic"
  [attribs]
  (c/map->Characteristic (merge {:name (d/unique-str "name")
                                 :description "dummy"
                                 :data-type "dummy"
                                 :unit "dummy"
                                 :value "dummy"}
                                attribs)))
(defn platform
  "Return a platform based on platform attribs"
  [attribs]
  (c/map->Platform (merge {:short-name (d/unique-str "short-name")
                           :long-name (d/unique-str "long-name")
                           :type (d/unique-str "Type")}
                          attribs)))
(defn platforms
  "Return a sequence of platforms with the given short names"
  [& short-names]
  (map #(c/map->Platform
          {:short-name %
           :long-name (d/unique-str "long-name")
           :type (d/unique-str "Type")})
       short-names))

(defn platform-with-instruments
  "Return a platform with a list of instruments"
  [short-name & instr-short-names]
  (let [instruments (apply instruments instr-short-names)]
    (c/map->Platform {:short-name short-name
                      :long-name (d/unique-str "long-name")
                      :type (d/unique-str "Type")
                      :instruments instruments})))

(defn platform-with-instrument-and-sensors
  "Return a platform with an instrument and a list of sensors"
  [plat-short-name instr-short-name & sensor-short-names]
  (let [instr-with-sensors (apply instrument-with-sensors instr-short-name sensor-short-names)]
    (c/map->Platform {:short-name plat-short-name
                      :long-name (d/unique-str "long-name")
                      :type (d/unique-str "Type")
                      :instruments [instr-with-sensors]})))

(defn projects
  "Return a sequence of projects with the given short names"
  [& short-names]
  (map #(c/map->Project
          {:short-name %
           :long-name (d/unique-str "long-name")})
       short-names))

(defn project
  "Return a project with the given short name and long name"
  [project-sn long-name]
  (c/map->Project
    {:short-name project-sn
     :long-name (d/unique-str long-name)}))

(defn org
  "Return archive/ processing center"
  [type center-name]
  (c/map->Organization
    {:type (keyword type)
     :org-name center-name}))

(defn related-url
  "Creates related url for online_only test"
  ([]
   (related-url nil))
  ([attribs]
   (let [description (d/unique-str "description")]
     (c/map->RelatedURL (merge {:url (d/unique-str "http://example.com/file")
                                :description description
                                :title description}
                               attribs)))))
(defn spatial
  [attributes]
  (let [{:keys [gsr sr orbit geometries]} attributes
        geometries (map (partial umm-s/set-coordinate-system sr) geometries)]
    (c/map->SpatialCoverage {:granule-spatial-representation gsr
                             :orbit-parameters (when orbit
                                                 (c/map->OrbitParameters orbit))
                             :spatial-representation sr
                             :geometries (seq geometries)})))

(defn personnel
  "Creates a Personnel record for the opendata tests."
  ([first-name last-name email]
   (personnel first-name last-name email "dummy"))
  ([first-name last-name email role]
   (let [contacts (when email
                    [(c/map->Contact {:type :email
                                      :value email})])]
     (c/map->Personnel {:first-name first-name
                        :last-name last-name
                        :contacts contacts
                        :roles [role]}))))

(defn collection
  "Returns a UmmCollection from the given attribute map. Various attribute keys are processed by
  different functions above."
  ([]
   (collection {}))
  ([attribs]
   (let [product (product attribs)
         data-provider-timestamps (data-provider-timestamps attribs)
         temporal {:temporal (temporal attribs)}
         minimal-coll {:entry-title (str (:long-name product) " " (:version-id product))
                       :summary (:long-name product)
                       :product product
                       :data-provider-timestamps data-provider-timestamps}
         attribs (select-keys attribs (concat (util/record-fields UmmCollection)
                                              [:concept-id :revision-id :native-id]))
         attribs (merge minimal-coll temporal attribs)]
     (c/map->UmmCollection attribs))))

(defn collection-dif
  "Creates a dif collection"
  ([]
   (collection-dif {}))
  ([attribs]
   (let [;; The following fields are needed for DIF to pass xml validation
         required-extra-dif-fields {:science-keywords [(science-keyword {:category "upcase"
                                                                         :topic "Cool"
                                                                         :term "Mild"})]
                                    :organizations [(org :distribution-center "Larc")]}
         attribs (merge required-extra-dif-fields {:version-id eid/DEFAULT_VERSION} attribs)]
     (collection attribs))))

(defn collection-dif10
  "Creates a dif collection"
  ([]
   (collection-dif10 {}))
  ([attribs]
   (let [;; The following fields are needed for DIF10 to pass xml validation
         required-extra-dif10-fields {:organizations [(org :distribution-center "Larc")]
                                      :science-keywords [(science-keyword {:category "upcase"
                                                                           :topic "Cool"
                                                                           :term "Mild"})]
                                      :platforms [(platform {:short-name "plat"
                                                             :type "Aircraft"
                                                             :instruments [(instrument {:short-name "inst"})]})]
                                      :projects (projects "proj")
                                      :spatial-coverage (spatial {:gsr :cartesian})
                                      :related-urls [(related-url {:type nil :url "http://www.foo.com"})]
                                      :beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                      :ending-date-time "1967-12-12T07:00:00.000-05:00"}
         attribs (merge required-extra-dif10-fields attribs)]
     (collection attribs))))

(defn collection-smap
  "Creates an smap collection"
  ([]
   (collection-smap {}))
  ([attribs]
   (let [collection (collection attribs)]
     (update-in collection [:data-provider-timestamps :revision-date-time]
                (fn [revision-date-time]
                  (some->> revision-date-time
                           (f/unparse (f/formatters :date))
                           (f/parse (f/formatters :date))))))))

(defn collection-concept
  "Returns the collection for ingest with the given attributes"
  ([attribs]
   (collection-concept attribs :echo10))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id]} attribs]
     (-> attribs
         collection
         (assoc :provider-id provider-id :native-id native-id)
         (d/item->concept concept-format)))))
