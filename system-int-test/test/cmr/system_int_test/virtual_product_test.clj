(ns cmr.system-int-test.virtual-product-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.virtual-product.config :as vp-config]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [cmr.common.time-keeper :as tk]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.collection :as umm-c]
            [cmr.umm.granule :as umm-g]
            [clj-time.core :as t]))

(use-fixtures :each (ingest/reset-fixture (into {"PROV_guid" "PROV"}
                                                (for [p vp/virtual-product-providers]
                                                  [(str p "_guid") p]))))

(defn- assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs {:keys [refs]}]
  (is (= (set expected-granule-urs)
         (set (map :name refs)))))

(comment
  (do
    (dev-sys-util/reset)
    (doseq [p virtual-product-providers]
      (ingest/create-provider (str p "_guid") p)))

  (dissoc (first isc) :revision-id :native-id :concept-id :entry-id)


  (def isc (map #(ingest-source (:provider-id %) %) (vp/source-collections)))

  (def vpc (vp/ingest-virtual-collections isc))

  )

(defmulti ingest-source
  "A method to ingest a source granule and collection concept while adding necessary attributes that allow all related virtual granules to pass through all the associated matchers"
  (fn [provider-id concept & options]
    [provider-id (type concept)]))

(defmethod ingest-source :default
  [provider-id concept & options]
  (d/ingest provider-id concept (apply hash-map options)))

(defmethod ingest-source ["LPDAAC_ECS" cmr.umm.collection.UmmCollection]
  [provider-id concept & options]
  (let [psa1 (dc/psa "TIR_ObservationMode" :string)
        psa2 (dc/psa "SWIR_ObservationMode" :string)
        psa3 (dc/psa "VNIR1_ObservationMode" :string)
        psa4 (dc/psa "VNIR2_ObservationMode" :string)]
    (d/ingest provider-id
              (assoc concept :product-specific-attributes [psa1 psa2 psa3 psa4])
              (apply hash-map options))))

(defmethod ingest-source ["LPDAAC_ECS" cmr.umm.granule.UmmGranule]
  [provider-id concept & options]
  (let [psa1 (dg/psa "TIR_ObservationMode" ["ON"])
        psa2 (dg/psa "SWIR_ObservationMode" ["ON"])
        psa3 (dg/psa "VNIR1_ObservationMode" ["ON"])
        psa4 (dg/psa "VNIR2_ObservationMode" ["ON"])]
    (d/ingest provider-id (-> concept
                              (assoc :data-granule
                                     (umm-g/map->DataGranule
                                       {:day-night "DAY"
                                        :production-date-time "2014-09-26T11:11:00Z"}))
                              (assoc :product-specific-attributes [psa1 psa2 psa3 psa4]))
              (apply hash-map options))))

;; TODO when testing a failure case we can delete the virtual collection. This would make the granule fail ingest.

(deftest specific-granule-in-virtual-product-test
  (let [ast-coll (ingest-source "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                              :projects (dc/projects "proj1" "proj2" "proj3")}))
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (ingest-source "LPDAAC_ECS"
                                              (dg/granule ast-coll {:granule-ur granule-ur
                                                                  :project-refs ["proj1"]}))
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)

    (util/are2 [expected query-params]
               (= (set expected)
                  (set (map :name (:refs (search/find-refs :granule query-params)))))

               "Find all granules"
               all-expected-granule-urs {:page-size 50}

               "Find all granules in a virtual collection using source granule-ur as an additional
               attribute"
               expected-granule-urs
               {"attribute[]" (format "string,%s,%s" vp-config/source-granule-ur-additional-attr-name
                                      granule-ur)
                :page-size 50}

               "Find virtual granule by shared fields"
               all-expected-granule-urs {:page-size 50 :project "proj1"})

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls]
        (assert-matching-granule-urs
          [(vp-config/generate-granule-ur
             "LPDAAC_ECS" "AST_L1A" (get-in vp-coll [:product :short-name]) granule-ur)]
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))

    (testing "Update source granule"
      ;; Update the source granule so that the projects referenced are different than original
      (let [ast-l1a-gran-r2 (ingest-source "LPDAAC_ECS" (assoc ast-l1a-gran
                                                          :project-refs ["proj2" "proj3"]
                                                          :revision-id nil))]
        (index/wait-until-indexed)
        (testing "find none by original project"
          (is (= 0 (:hits (search/find-refs :granule {:project "proj1"})))))

        (testing "Find virtual granule by shared fields"
          (assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 50
                                        :project "proj2"})))))

    (testing "Delete source granule"
      (let [resp (ingest/delete-concept (d/item->concept ast-l1a-gran))]
        (is (= 200 (:status resp)) (pr-str resp)))
      (index/wait-until-indexed)

      (testing "Find no granules"
        (is (= 0 (:hits (search/find-refs :granule {}))))))

    (testing "Recreate source granule"
      (let [ast-l1a-gran-r4 (ingest-source "LPDAAC_ECS"
                                                     (dissoc ast-l1a-gran :revision-id :concept-id))]
        (index/wait-until-indexed)
        (testing "Find all granules"
          (assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 50})))))))

(deftest all-granules-in-virtual-product-test
  (let [source-collections (map #(ingest-source (:provider-id %) %) (vp/source-collections))
        ;; Ingest the virtual collections. For each virtual collection associate it with the source
        ;; collection to use later.
        vp-colls (reduce (fn [new-colls source-coll]
                           (into new-colls (map #(assoc % :source-collection source-coll)
                                                (vp/ingest-virtual-collections [source-coll]))))
                         []
                         source-collections)
        source-granules (doall (for [source-coll source-collections
                                     :let [{:keys [provider-id entry-title]} source-coll]
                                     granule-ur (vp-config/sample-source-granule-urs
                                                  [provider-id entry-title])]
                                 (ingest-source provider-id
                                                          (dg/granule source-coll {:granule-ur granule-ur}))))
        all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs source-granules)
                                         (map :granule-ur source-granules))]
    (index/wait-until-indexed)

    (testing "Find all granules"
      (assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 50})))

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls
              :let [{:keys [provider-id source-collection]} vp-coll
                    source-short-name (get-in source-collection [:product :short-name])
                    vp-short-name (get-in vp-coll [:product :short-name])]]
        (assert-matching-granule-urs
          (map #(vp-config/generate-granule-ur provider-id source-short-name vp-short-name %)
               (vp-config/sample-source-granule-urs
                 [provider-id (:entry-title source-collection)]))
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))))

(defn- assert-virtual-gran-revision-id
  "Assert that the revision ids of the granules of the collections vp-colls match expected-revision-id"
  [vp-colls expected-revision-id]
  (doseq [revision-id  (mapcat #(map :revision-id (:refs (search/find-refs
                                                           :granule {:entry-title (:entry-title %)
                                                                     :page-size 50}))) vp-colls)]
    (is (= expected-revision-id revision-id))))

(defn- assert-tombstones
  "Assert that the concepts with the given concept-ids and revision-id exist in mdb and are tombstones"
  [concept-ids revision-id]
  (doseq [concept-id concept-ids]
    (is (:deleted (ingest/get-concept concept-id revision-id)))))

;; Verify that latest revision ids of virtual granules and the corresponding source granules
;; are in sync as various ingest operations are performed on the source granules
(deftest revision-ids-in-sync-test
  (let [ast-coll (ingest-source "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"}))
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (dg/granule ast-coll {:granule-ur granule-ur})
        ingest-result (ingest-source "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 5))
        _ (index/wait-until-indexed)
        vp-granule-ids (mapcat #(map :id (:refs (search/find-refs
                                                  :granule {:entry-title (:entry-title %)
                                                            :page-size 50}))) vp-colls)]

    ;; check revision ids are in sync after ingest/update operations
    (assert-virtual-gran-revision-id vp-colls 5)
    (ingest-source "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 10))
    (index/wait-until-indexed)
    (assert-virtual-gran-revision-id vp-colls 10)

    ;; check revision ids are in sync after delete operations
    (ingest/delete-concept (d/item->concept ingest-result) {:revision-id 12})
    (index/wait-until-indexed)
    (assert-tombstones vp-granule-ids 12)
    (ingest/delete-concept (d/item->concept ingest-result) {:revision-id 14})
    (index/wait-until-indexed)
    (assert-tombstones vp-granule-ids 14)))

(defn- get-granule-entry-triplet
  "Get granule entry triplet consisting of entry title, concept id and granule ur for the
  granule in the collection with the given entry-title."
  [coll]
  (let [entry-title (:entry-title coll)
        granule-refs (:refs (search/find-refs
                              :granule {:entry-title entry-title
                                        :provider-id (:provider-id coll)
                                        :page-size 1}))
        {granule-id :id granule-ur :name} (first granule-refs)]
    {:entry-title entry-title
     :concept-id granule-id
     :granule-ur granule-ur}))

(defn- granule->entry
  [granule]
  {:entry-title (get-in granule [:collection-ref :entry-title])
   :concept-id (:concept-id granule)
   :granule-ur (:granule-ur granule)})

(deftest translate-granule-entries-test
  (let [ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        ast-coll (ingest-source "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title ast-entry-title}))
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        ast-gran (ingest-source "LPDAAC_ECS"
                           (dg/granule ast-coll {:granule-ur "SC:AST_L1A.003:2006227720"}))
        prov-ast-coll (ingest-source "PROV"
                                (dc/collection
                                  {:entry-title ast-entry-title}))
        prov-ast-gran (ingest-source "PROV"
                                (dg/granule prov-ast-coll {:granule-ur "SC:AST_L1A.003:2006227720"}))

        lpdaac-non-ast-coll (ingest-source "LPDAAC_ECS"
                                      (dc/collection
                                        {:entry-title "non virtual entry title"}))
        lpdaac-non-ast-gran (ingest-source "LPDAAC_ECS"
                                      (dg/granule lpdaac-non-ast-coll {:granule-ur "granule-ur2"}))
        prov-coll (ingest-source "PROV"
                            (dc/collection
                              {:entry-title "some other entry title"}))
        prov-gran1 (ingest-source "PROV" (dg/granule prov-coll {:granule-ur "granule-ur3"}))

        prov-gran2 (ingest-source "PROV" (dg/granule prov-coll {:granule-ur "granule-ur4"}))


        _ (index/wait-until-indexed)
        source-granule   (granule->entry ast-gran)
        virtual-granule1 (get-granule-entry-triplet (first vp-colls))
        virtual-granule2 (get-granule-entry-triplet (second vp-colls))
        virtual-granule3 (get-granule-entry-triplet (nth vp-colls 2))
        virtual-granule4 (get-granule-entry-triplet (nth vp-colls 3))

        ;; Granule with same granule ur and entry title as an AST granule but belonging
        ;; to a different provider than AST collection
        non-virtual-granule1 (granule->entry prov-ast-gran)

        ;; Another granule under the same provider as the AST collection but beloning to a
        ;; a different collection
        non-virtual-granule2 (granule->entry lpdaac-non-ast-gran)

        ;; A random non-virtual granule
        non-virtual-granule3 (granule->entry prov-gran1)

        ;; Another random non-virtual granule which belongs to the same collection as
        ;; non-virtual-granule3
        non-virtual-granule4 (granule->entry prov-gran2)]

    (testing "Valid input to translate-granule-entries end-point"
      (util/are2 [request-json expected-response-json]
                 (let [response (vp/translate-granule-entries
                                  (json/generate-string request-json))]
                   (= expected-response-json (json/parse-string (:body response) true)))

                 "Input with no virtual granules should return the original response"
                 [non-virtual-granule1 non-virtual-granule2]
                 [non-virtual-granule1 non-virtual-granule2]

                 "Input with two non-virtual granules from the same dataset should return the
                 original response"
                 [non-virtual-granule3 non-virtual-granule4]
                 [non-virtual-granule3 non-virtual-granule4]

                 "Virtual granule should be translated to corresponding source granule"
                 [non-virtual-granule1 virtual-granule1]
                 [non-virtual-granule1 source-granule]

                 "The order of the output granules should match the corresponding input. Duplicates
                 in the source should be preserved."
                 [source-granule non-virtual-granule1 virtual-granule1 non-virtual-granule2
                  non-virtual-granule3 non-virtual-granule4 virtual-granule2 virtual-granule3
                  non-virtual-granule3 non-virtual-granule3 virtual-granule4 source-granule
                  non-virtual-granule1 virtual-granule1 ]
                 [source-granule non-virtual-granule1 source-granule non-virtual-granule2
                  non-virtual-granule3 non-virtual-granule4 source-granule source-granule
                  non-virtual-granule3 non-virtual-granule3 source-granule source-granule
                  non-virtual-granule1 source-granule]))

    (testing "Translating a granule which is deleted"
      (util/are2 [deleted-granule request-json expected-response-json]
                 (let [_ (ingest/delete-concept (d/item->concept deleted-granule))
                       _ (index/wait-until-indexed)
                       response (vp/translate-granule-entries
                                  (json/generate-string request-json))]
                   (= expected-response-json (json/parse-string (:body response) true)))

                 "A non-virtual granule deleted while ordering"
                 lpdaac-non-ast-gran
                 [non-virtual-granule1 non-virtual-granule2 source-granule]
                 [non-virtual-granule1 nil source-granule]

                 "A source granule deleted while ordering"
                 ast-gran
                 [virtual-granule1 non-virtual-granule1 source-granule virtual-granule2]
                 [nil non-virtual-granule1 nil nil]))

    (testing "Malformed JSON"
      (let [malformed-json (str/replace (json/generate-string [virtual-granule1]) #"}" "]")
            response (vp/translate-granule-entries malformed-json)
            errors (:errors (json/parse-string (:body response) true))]
        (is (= 1 (count errors)))
        (is (.startsWith (first errors) "Invalid JSON: Unexpected close marker ']': expected '}'"))))

    (testing "Invalid input to translate-granule-items end-point should result in error"
      (let [invalid-json (json/generate-string [virtual-granule1
                                                (dissoc virtual-granule1 :concept-id)])
            response (vp/translate-granule-entries invalid-json)
            errors (:errors (json/parse-string (:body response) true))]
        (and (= 400 (:status response))
             (= ["/1 object has missing required properties ([\"concept-id\"])"] errors))))))

(deftest virtual-product-non-cmr-only-provider-test
  (let [_ (ingest/update-ingest-provider {:provider-id "LPDAAC_ECS"
                                          :short-name "LPDAAC_ECS"
                                          :cmr-only false
                                          :small false})
        ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        ast-coll (ingest-source "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title ast-entry-title}) :client-id "ECHO")
        vp-colls (vp/ingest-virtual-collections [ast-coll] {:client-id "ECHO"})
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (ingest-source "LPDAAC_ECS"
                                              (dg/granule ast-coll {:granule-ur granule-ur})
                                              :client-id "ECHO")
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)
    (assert-matching-granule-urs
      all-expected-granule-urs
      (search/find-refs :granule {:page-size 50}))))

(defn- get-virtual-granule-umms
  [src-granule-ur]
  (let [query-param {"attribute[]" (format "string,%s,%s"
                                           vp-config/source-granule-ur-additional-attr-name
                                           src-granule-ur)
                     :page-size 20}
        virt-gran-refs (:refs (search/find-refs :granule query-param))]
    (map #(-> % :id search/get-concept-by-concept-id :body g/parse-granule) virt-gran-refs)))

(deftest omi-aura-configuration-test
  (let [omi-coll (ingest-source "GSFCS4PA"
                           (dc/collection
                             {:entry-title (str "OMI/Aura Surface UVB Irradiance and Erythemal"
                                                " Dose Daily L3 Global 1.0x1.0 deg Grid V003")}))
        vp-colls (vp/ingest-virtual-collections [omi-coll])
        granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2004m1001_v003-2013m0314t081851.he5"
        [ur-prefix ur-suffix] (str/split granule-ur #":")
        data-path "http://acdisc.gsfc.nasa.gov/data/s4pa///Aura_OMI_Level3/OMUVBd.003/2013/"
        opendap-path "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2013/"]

    (util/are2 [src-granule-ur source-related-urls expected-related-url-maps]
               (let [_ (ingest-source "GSFCS4PA" (dg/granule
                                                     omi-coll {:granule-ur src-granule-ur
                                                               :related-urls source-related-urls}))
                     _ (index/wait-until-indexed)
                     virt-gran-umm (first (get-virtual-granule-umms src-granule-ur))
                     expected-related-urls (map #(umm-c/map->RelatedURL %) expected-related-url-maps)]
                 (= (set expected-related-urls) (set (:related-urls virt-gran-umm))))

               "Related urls with only one access url which matches the pattern"
               granule-ur
               [{:url (str data-path ur-suffix) :type "GET DATA"}]
               [{:url (str opendap-path ur-suffix ".nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")
                 :type "GET DATA"}]

               "Related urls with only one access url which matches the pattern, but is not
               an online access url"
               granule-ur
               [{:url (str data-path ur-suffix)}]
               ;; Some additional attributes are added by CMR automatically, but url remains the same
               [{:url (str data-path ur-suffix)
                 :type "VIEW RELATED INFORMATION"
                 :title "(USER SUPPORT)"}]

               "Related urls with only one access url which does not match the pattern"
               granule-ur
               [{:url (str data-path "random.he5") :type "GET DATA"}]
               [{:url (str data-path "random.he5") :type "GET DATA"}]

               "Multiple related urls"
               granule-ur
               [{:url (str data-path ur-suffix) :type "GET DATA"}
                {:url "http://www.foo.com"}]
               [{:url (str opendap-path ur-suffix ".nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")
                 :type "GET DATA"}
                {:url "http://www.foo.com"
                 :type "VIEW RELATED INFORMATION"
                 :title "(USER SUPPORT)"}]

               "Unexpected format for the granule UR should not result in translation"
               "OMI-Aura_L3-OMUVBd_2004m1001_v003-2013m0314t081851.he5" ;; no ":"
               [{:url (str data-path ur-suffix) :type "GET DATA"}]
               [{:url (str data-path ur-suffix) :type "GET DATA"}])))

(deftest ast-granule-umm-matchers
  (let [ast-psas [(dc/psa "TIR_ObservationMode" :string)
                  (dc/psa "SWIR_ObservationMode" :string)
                  (dc/psa "VNIR1_ObservationMode" :string)
                  (dc/psa "VNIR2_ObservationMode" :string)]
        ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                              :product-specific-attributes ast-psas}))
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        psa1 (dg/psa "TIR_ObservationMode" ["ON"])
        psa2 (dg/psa "SWIR_ObservationMode" ["ON"])
        psa3 (dg/psa "VNIR1_ObservationMode" ["ON"])
        psa4 (dg/psa "VNIR2_ObservationMode" ["ON"])]
    (are [granule-attrs expected-granule-urs]
         (let [_ (d/ingest "LPDAAC_ECS" (dg/granule ast-coll granule-attrs))
               _ (index/wait-until-indexed)
               src-granule-ur (:granule-ur granule-attrs)
               query-param {"attribute[]" (format "string,%s,%s"
                                                  vp-config/source-granule-ur-additional-attr-name
                                                  src-granule-ur)
                            :page-size 20}
               refs (search/find-refs :granule query-param)]
           (assert-matching-granule-urs expected-granule-urs refs))

         {:granule-ur "SC:AST_L1A.003:2006227720"
          :product-specific-attributes [psa4]}
         []

         {:granule-ur "SC:AST_L1A.003:2006227721"
          :product-specific-attributes [psa1]}
         ["SC:AST_05.003:2006227721" "SC:AST_08.003:2006227721" "SC:AST_09T.003:2006227721"]

         {:granule-ur "SC:AST_L1A.003:2006227722"
          :product-specific-attributes [psa1 psa2 psa3 psa4]}
         ["SC:AST_05.003:2006227722" "SC:AST_08.003:2006227722" "SC:AST_09T.003:2006227722"]

         {:granule-ur "SC:AST_L1A.003:2006227724"
          :product-specific-attributes [psa3 psa4]
          :data-granule (umm-g/map->DataGranule
                          {:day-night "DAY"
                           :production-date-time "2014-09-26T11:11:00Z"})}
         ["SC:AST14DEM.003:2006227724" "SC:AST14OTH.003:2006227724" "SC:AST14DMO.003:2006227724"]

         {:granule-ur "SC:AST_L1A.003:2006227725"
          :product-specific-attributes [psa2 psa3 psa4]
          :data-granule (umm-g/map->DataGranule
                          {:day-night "DAY"
                           :production-date-time "2014-09-26T11:11:00Z"})}
         ["SC:AST14DMO.003:2006227725" "SC:AST_09.003:2006227725"
          "SC:AST_09XT.003:2006227725" "SC:AST14DEM.003:2006227725"
          "SC:AST_07.003:2006227725"   "SC:AST14OTH.003:2006227725"
          "SC:AST_07XT.003:2006227725"]

         {:granule-ur "SC:AST_L1A.003:2006227726"
          :product-specific-attributes [psa1 psa2 psa3 psa4]
          :data-granule (umm-g/map->DataGranule
                          {:day-night "DAY"
                           :production-date-time "2014-09-26T11:11:00Z"})}
         ["SC:AST_09XT.003:2006227726" "SC:AST14DEM.003:2006227726"
          "SC:AST_08.003:2006227726"   "SC:AST_05.003:2006227726"
          "SC:AST14OTH.003:2006227726" "SC:AST_07.003:2006227726"
          "SC:AST_09.003:2006227726"   "SC:AST_09T.003:2006227726"
          "SC:AST_07XT.003:2006227726" "SC:AST14DMO.003:2006227726"])))
