(ns cmr.system-int-test.data2.collection
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.collection :as c]
            [cmr.common.util :as util]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.collection.temporal :as ct]
            [cmr.umm.spatial :as umm-s])
  (:import [cmr.umm.collection
            Product
            DataProviderTimestamps
            ScienceKeyword
            UmmCollection]))


(comment

  (type (collection {:foo 4 :short-name "foo"}))
  (product {:foo 4 :short-name "foo"})

  (type (first (cmr.umm.collection.ProductSpecificAttribute/getBasis)))
  (projects {:campaigns [{:short-name "xx" :long-name "lxx"} {:short-name "yy" :long-name "lyy"}]})
  )

(defn psa
  "Creates product specific attribute"
  ([name type]
   (psa name type nil))
  ([name type value]
   (psa name type value "Generated"))
  ([name type value desc]
   (c/map->ProductSpecificAttribute
     {:name name
      :description desc
      :data-type type
      :value value})))

(defn two-d
  "Creates two-d-coordinate-system specific attribute"
  [name]
  (c/map->TwoDCoordinateSystem
    {:name name}))

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
                            :update-time (d/make-datetime 18 false)}]
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
  ([sensor-sn]
   (sensor sensor-sn nil nil))
  ([sensor-sn long-name]
   (c/map->Sensor {:short-name sensor-sn
                   :long-name long-name}))
  ([sensor-sn long-name technique]
   (c/map->Sensor {:short-name sensor-sn
                   :long-name long-name
                   :technique technique})))

(defn instrument
  "Return an instrument based on instrument attribs"
  ([instrument-sn]
   (instrument instrument-sn nil nil))
  ([instrument-sn instrument-ln]
   (instrument instrument-sn instrument-ln nil))
  ([instrument-sn long-name technique & sensors]
   (c/map->Instrument
     {:short-name instrument-sn
      :long-name long-name
      :technique technique
      :sensors sensors})))

(defn characteristic
  "Returns a platform characteristic"
  ([name]
   (characteristic name nil))
  ([name description]
   (c/map->Characteristic {:name name :description description
                           :data-type "dummy"
                           :unit "dummy"
                           :value "dummy"})))

(defn platform
  "Return a platform based on platform attribs"
  ([platform-sn]
   (platform platform-sn (d/unique-str "long-name")))
  ([platform-sn long-name]
   (platform platform-sn long-name nil nil))
  ([platform-sn long-name characteristics]
   (platform platform-sn long-name characteristics nil))
  ([platform-sn long-name characteristics & instruments]
   (c/map->Platform
     {:short-name platform-sn
      :long-name long-name
      :type (d/unique-str "Type")
      :characteristics characteristics
      :instruments (if (= [nil] instruments) nil instruments)})))

(defn projects
  "Return a sequence of projects with the given short names"
  [& short-names]
  (map #(c/map->Project
          {:short-name %
           :long-name (d/unique-str "long-name")})
       short-names))

(defn org
  "Return archive/ processing center"
  [type center-name]
  (c/map->Organization
    {:type (keyword type)
     :org-name center-name}))

(defn related-url
  "Creates related url for online_only test"
  ([type]
   (related-url type (d/unique-str "http://example.com/file")))
  ([type url]
   (related-url type nil url))
  ([type mime-type url]
   (let [description (d/unique-str "description")]
     (c/map->RelatedURL {:type type
                         :url url
                         :description description
                         :title description
                         :mime-type mime-type}))))

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
  [first-name last-name email]
  (let [contacts (when email
                   [(c/map->Contact {:type :email
                                     :value email})])]
    (c/map->Personnel {:first-name first-name
                       :last-name last-name
                       :contacts contacts
                       :roles ["dummy"]})))

(defn collection
  "Creates a collection"
  ([]
   (collection {}))
  ([attribs]
   (let [product (product attribs)
         data-provider-timestamps (data-provider-timestamps attribs)
         temporal {:temporal (temporal attribs)}
         minimal-coll {:entry-id (str (:short-name product) "_" (:version-id product))
                       :entry-title (str (:long-name product) " " (:version-id product))
                       :summary (:long-name product)
                       :product product
                       :data-provider-timestamps data-provider-timestamps}
         attribs (select-keys attribs (concat (util/record-fields UmmCollection) [:concept-id :revision-id]))
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
         attribs (merge required-extra-dif-fields attribs)]
     (collection attribs))))

(defn- add-native-id
  "Add native-id to concept if applicable, returns the concept"
  [concept native-id]
  (if native-id
    (assoc concept :native-id native-id)
    concept))

(defn collection-for-ingest
  "Returns the collection for ingest with the given attributes"
  ([attribs]
   (collection-for-ingest attribs :echo10))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id entry-id]} attribs
         provider-id (or provider-id "PROV1")]
     (-> attribs
         collection
         (d/item->concept concept-format)
         (assoc :provider-id provider-id)
         (add-native-id native-id)))))
