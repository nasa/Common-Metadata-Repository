(ns cmr.system-int-test.data2.umm-json
  (require [clojure.test :refer :all]
           [cheshire.core :as json]
           [cmr.common.util :as util]
           [cmr.common.mime-types :as mt]
           [cmr.umm.umm-core :as umm-lib]
           [cmr.umm-spec.legacy :as umm-legacy]
           [cmr.spatial.mbr :as mbr]
           [cmr.system-int-test.data2.core :as d]
           [cmr.system-int-test.data2.collection :as dc]
           [cmr.umm-spec.umm-spec-core :as umm-spec]
           [cmr.umm-spec.json-schema :as umm-json-schema]
           [cmr.umm-spec.test.location-keywords-helper :as lkt]
           [cmr.umm.collection.entry-id :as eid]))

(def test-context (lkt/setup-context-for-test))

(defn- collection->umm-json-meta
  "Returns the meta section of umm-json format."
  [collection]
  (let [{:keys [user-id format-key
                revision-id concept-id provider-id deleted]} collection]
    (util/remove-nil-keys
     {:concept-type "collection"
      :concept-id concept-id
      :revision-id revision-id
      :native-id (or (:entry-title collection) (:EntryTitle collection))
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type format-key)
      :deleted (boolean deleted)})))

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

(defn assert-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [version collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-umm-json-search-result (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(collection->umm-json version %) collections))
             (set (map #(util/dissoc-in % [:meta :revision-date])
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
