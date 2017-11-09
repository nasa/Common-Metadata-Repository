(ns cmr.system-int-test.data2.umm-json
  "Contains helper functions for UMM JSON testing."
  (require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.spatial.mbr :as mbr]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.umm-spec.json-schema :as umm-json-schema]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm.collection.entry-id :as eid]
   [cmr.umm.umm-core :as umm-lib]))

(def test-context (lkt/setup-context-for-test))

(defn- collection->umm-json-meta
  "Returns the meta section of umm-json format."
  [collection]
  (let [{:keys [user-id format-key revision-id concept-id provider-id deleted
                has-variables has-formats has-transforms has-spatial-subsetting
                variables services]} collection]
    (util/remove-nil-keys
     {:concept-type "collection"
      :concept-id concept-id
      :revision-id revision-id
      :native-id (or (:entry-title collection) (:EntryTitle collection))
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type format-key)
      :deleted (boolean deleted)
      :has-variables (when-not deleted (boolean has-variables))
      :has-formats (when-not deleted (boolean has-formats))
      :has-transforms (when-not deleted (boolean has-transforms))
      :has-spatial-subsetting (when-not deleted (boolean has-spatial-subsetting))
      :associations (when (or (seq services) (seq variables))
                      (util/remove-map-keys empty? {:variables (set variables)
                                                    :services (set services)}))})))

(defn- collection->legacy-umm-json
  "Returns the response of a search in legacy UMM JSON format. The UMM JSON search response format was
   originally created with a umm field which contained a few collection fields but was not UMM JSON."
  [collection]
  (let [{{:keys [short-name version-id]} :product
         :keys [entry-title]} collection]
    {:meta (collection->umm-json-meta collection)
     :umm {:entry-id (eid/entry-id short-name version-id)
           :entry-title entry-title
           :short-name short-name
           :version-id version-id}}))

(defn assert-legacy-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (is (= (set (map collection->legacy-umm-json collections))
           (set (map #(util/dissoc-in % [:meta :revision-date])
                     (get-in search-result [:results :items])))))))

(defn- collection->umm-json
  "Returns the response of a search in UMM JSON format."
  [version collection]
  (if (:deleted collection)
    {:meta (collection->umm-json-meta collection)}
    (let [ingested-metadata (umm-legacy/generate-metadata
                             test-context (d/remove-ingest-associated-keys collection) (:format-key collection))
          umm-spec-record (umm-spec/parse-metadata
                           test-context :collection (:format-key collection) ingested-metadata)
          umm-json (umm-spec/generate-metadata
                    test-context umm-spec-record {:format :umm-json :version version})]
      {:meta (collection->umm-json-meta collection)
       :umm (json/decode umm-json true)})))

(defn- meta-for-comparison
  "Returns the meta part of UMM JSON for comparision for the given meta"
  [meta]
  (util/remove-nil-keys
   (-> meta
       (dissoc :revision-date)
       (update :associations (fn [assocs]
                               (when (seq assocs)
                                 (util/map-values set assocs)))))))

(defn assert-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [version collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-collection-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(collection->umm-json version %) collections))
             (set (map #(update % :meta meta-for-comparison)
                       (get-in search-result [:results :items]))))))))

(defn- variable->umm-json-meta
  "Returns the meta section of umm-json format."
  [variable]
  (let [{:keys [user-id revision-id concept-id provider-id native-id deleted]} variable
        deleted (boolean deleted)
        meta (util/remove-nil-keys
              {:concept-type "variable"
               :concept-id concept-id
               :revision-id revision-id
               :native-id native-id
               :user-id user-id
               :provider-id provider-id
               :deleted deleted})]
    (if deleted
      meta
      (assoc meta :format mt/umm-json))))

(defn- variable->umm-json
  "Returns the UMM JSON result of the given variable."
  [version variable]
  (if (:deleted variable)
    {:meta (variable->umm-json-meta variable)
     :associations {:collections #{}}}
    (let [;; use the original metadata for now, add version migration when Variable versioning is added
           {:keys [metadata associated-collections]} variable]
      {:meta (variable->umm-json-meta variable)
       :umm (json/decode metadata true)
       :associations {:collections (set associated-collections)}})))

(defn- result-item-for-comparison
  "Returns the result item for comparison purpose,
  i.e. drop revision-date and change associated collections to a set."
  [item]
  (-> item
      (util/dissoc-in [:meta :revision-date])
      (update-in [:associations :collections] set)))

(defn assert-variable-umm-jsons-match
  "Returns true if the UMM variable umm-jsons match the umm-jsons returned from the search."
  [version variables search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-variable-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(variable->umm-json version %) variables))
             (set (map result-item-for-comparison
                       (get-in search-result [:results :items]))))))))

(defn minimum-umm-spec-fields
  "the minimum valid fields for a UMM lib collection to be valid with UMM Spec"
  []
  {:platforms [(dc/platform {:short-name "platform" :type "Not provided"})]
   :processing-level-id "processing"
   :related-urls [(dc/related-url {:type "GET DATA"})]
   :science-keywords [(dc/science-keyword {:category "Cat1"
                                           :topic "Topic1"
                                           :term "Term1"})]
   :spatial-coverage (dc/spatial {:gsr :geodetic, :sr :cartesian, :geometries [mbr/whole-world]})
   :beginning-date-time "2000-01-01T00:00:00Z"
   :ending-date-time "2001-01-01T00:00:00Z"})

(defn umm-spec-collection
  "Creates a UMM lib collection with the minimum valid fields for UMM Spec"
  [attribs]
  (dc/collection (merge (minimum-umm-spec-fields) attribs)))
