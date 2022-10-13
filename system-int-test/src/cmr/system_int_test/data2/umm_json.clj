(ns cmr.system-int-test.data2.umm-json
  "Contains helper functions for UMM JSON testing."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.results-handler-util :as rs-util]
   [cmr.spatial.mbr :as mbr]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.umm-spec.json-schema :as umm-json-schema]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm.collection.entry-id :as eid]))

(def test-context (lkt/setup-context-for-test))

(defn- collection->umm-json-meta
  "Returns the meta section of umm-json format."
  [collection]
  (let [{:keys [user-id format-key revision-id concept-id provider-id deleted
                has-variables has-formats has-transforms has-spatial-subsetting
                has-temporal-subsetting variables services tools s3-bucket-and-object-prefix-names]} collection
        associations (when (or (seq services) (seq variables) (seq tools))
                       (util/remove-map-keys empty? {:variables (set variables)
                                                     :tools (set tools)
                                                     :services (set services)}))]
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
      :has-temporal-subsetting (when-not deleted (boolean has-temporal-subsetting))
      :s3-links s3-bucket-and-object-prefix-names
      :associations associations
      :association-details (when associations
                             (util/map-values set (rs-util/build-association-details associations concept-id)))})))

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

(defn- legacy-meta-for-comparison
  "Returns the meta part of LEGACY UMM JSON for comparision for the given meta"
  [meta]
  (util/remove-nil-keys
   (dissoc meta :granule-count :revision-date)))

(defn assert-legacy-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (is (= (set (map collection->legacy-umm-json collections))
           (set (map #(update % :meta legacy-meta-for-comparison)
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
       (dissoc :granule-count)
       (dissoc :revision-date)
       (update :associations (fn [assocs]
                               (when (seq assocs)
                                 (util/map-values set assocs))))
       (update :association-details (fn [assocs]
                                      (when (seq assocs)
                                        (util/map-values set assocs)))))))

(defn assert-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [version collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result)))
          "Bad Mime-Type")
      (is (= version (mt/version-of (:content-type search-result))) "Bad Version")
      (is (nil? (util/seqv (umm-json-schema/validate-collection-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(collection->umm-json version %) collections))
             (set (map #(update % :meta meta-for-comparison)
                       (get-in search-result [:results :items]))))
          "Match of Content failed"))))

(defn- concept->umm-json-meta
  "Returns the meta section of umm-json format."
  [concept-type concept]
  (let [{:keys [user-id revision-id concept-id provider-id native-id deleted associations association-details]} concept
        deleted (boolean deleted)
        meta (util/remove-nil-keys
              {:concept-type (name concept-type)
               :concept-id concept-id
               :revision-id revision-id
               :native-id native-id
               :user-id user-id
               :provider-id provider-id
               :deleted deleted
               :associations associations
               :association-details association-details})]
    (if deleted
      meta
      (assoc meta :format mt/umm-json))))

(defn- granule->umm-json-meta
  "Returns the meta section of granule UMM JSON search result."
  [granule]
  (let [{:keys [concept-id revision-id provider-id native-id format]} granule]
    (util/remove-nil-keys
     {:concept-type "granule"
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :provider-id provider-id
      :format (mt/format->mime-type format)})))

(defn- granule->umm-json
  "Returns the UMM JSON search result of the given granule."
  [version granule]
  ;; Currently there is no version migration
  ;; We need to handle version migration later when it is supported for UMM-G
  (let [{:keys [metadata]} granule]
    {:meta (granule->umm-json-meta granule)
     :umm (json/decode metadata true)}))

(defn assert-granule-umm-jsons-match
  "Returns true if the UMM JSON response returned from the search matches the given granules."
  [version granules search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-granule-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(granule->umm-json version %) granules))
             (set (map #(util/dissoc-in % [:meta :revision-date])
                       (get-in search-result [:results :items]))))))))

(defn- variable->umm-json-meta
  "Returns the meta section of umm-json format."
  [variable]
  (concept->umm-json-meta :variable variable))

(defn- variable->umm-json
  "Returns the UMM JSON result of the given variable."
  [version variable]
  (if (:deleted variable)
    {:meta (merge {:user-id "ECHO_SYS"} (variable->umm-json-meta variable))
     :associations {:collections #{}}}
    (let [;; use the original metadata for now, add version migration when Variable versioning is added
          {:keys [metadata associated-collections associated-item]} variable]
      {:meta (merge {:user-id "ECHO_SYS"} (variable->umm-json-meta variable))
       :umm (json/decode metadata true)
       :associations (if (seq associated-collections)
                       {:collections (set associated-collections)}
                       {:collections (set [associated-item])})})))

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

(defn- service->umm-json-meta
  "Returns the meta section of umm-json format."
  [service]
  (concept->umm-json-meta :service service))

(defn- tool->umm-json-meta
  "Returns the meta section of umm-json format."
  [tool]
  (concept->umm-json-meta :tool tool))

(defn- subscription->umm-json-meta
  "Returns the meta section of umm-json format."
  [subscription]
  (concept->umm-json-meta :subscription subscription))

(defn- service->umm-json
  "Returns the UMM JSON result of the given service."
  [version service]
  (if (:deleted service)
    {:meta (service->umm-json-meta service)}
    (let [;; use the original metadata for now, add version migration when service versioning is added
          {:keys [metadata]} service]
      {:meta (service->umm-json-meta service)
       :umm (json/decode metadata true)})))

(defn- tool->umm-json
  "Returns the UMM JSON result of the given tool."
  [version tool]
  (if (:deleted tool)
    {:meta (tool->umm-json-meta tool)}
    (let [;; use the original metadata for now, add version migration when tool versioning is added
          {:keys [metadata]} tool]
      {:meta (tool->umm-json-meta tool)
       :umm (json/decode metadata true)})))

(defn- subscription->umm-json
  "Returns the UMM JSON result of the given subscription."
  [version subscription]
  (if (:deleted subscription)
    {:meta (subscription->umm-json-meta subscription)}
    (let [;; use the original metadata for now, add version migration when subscription versioning is added
          {:keys [metadata]} subscription]
      {:meta (subscription->umm-json-meta subscription)
       :umm (json/decode metadata true)})))

(defn assert-service-umm-jsons-match
  "Returns true if the UMM service umm-jsons match the umm-jsons returned from the search."
  [version services search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-service-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(service->umm-json version %) services))
             (set (map #(util/dissoc-in % [:meta :revision-date])
                       (get-in search-result [:results :items]))))))))

(defn assert-tool-umm-jsons-match
  "Returns true if the UMM tool umm-jsons match the umm-jsons returned from the search."
  [version tools search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-tool-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(tool->umm-json version %) tools))
             (set (map #(util/dissoc-in % [:meta :revision-date])
                       (get-in search-result [:results :items]))))))))

(defn assert-subscription-umm-jsons-match
  "Returns true if the UMM subscription umm-jsons match the umm-jsons returned from the search."
  [version subscriptions search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      (is (= mt/umm-json-results (mt/base-mime-type-of (:content-type search-result))))
      (is (= version (mt/version-of (:content-type search-result))))
      (is (nil? (util/seqv (umm-json-schema/validate-subscription-umm-json-search-result
                            (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(subscription->umm-json version %) subscriptions))
             (set (->> (get-in search-result [:results :items])
                       (map #(util/dissoc-in % [:meta :revision-date]))
                       (map #(util/dissoc-in % [:meta :creation-date])))))))))

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
