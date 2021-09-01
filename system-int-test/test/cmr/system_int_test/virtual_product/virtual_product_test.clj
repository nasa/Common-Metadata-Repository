(ns cmr.system-int-test.virtual-product.virtual-product-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.virtual-product-util :as vp]
   [cmr.umm.echo10.granule :as g]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.umm-spec.util :as umm-spec-util]
   [cmr.virtual-product.config]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]))

(use-fixtures :each (ingest/reset-fixture (into {"PROV_guid" "PROV"
                                                 "LP_ALIAS_guid" "LP_ALIAS"}
                                                (for [p vp/virtual-product-providers]
                                                  [(str p "_guid") p]))))

(comment
  (do
    (dev-sys-util/reset)
    (doseq [p vp/virtual-product-providers]
      (ingest/create-provider {:provider-guid (str p "_guid") :provider-id p})))

  (dissoc (first isc) :revision-id :native-id :concept-id :entry-id)


  (def isc (vp/ingest-source-collections))

  (def vpc (vp/ingest-virtual-collections isc)))

(deftest specific-granule-in-virtual-product-test
  (let [[ast-coll] (vp/ingest-source-collections
                     [(assoc
                        (dc/collection
                          {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                           :short-name "AST_L1A"
                           :projects (dc/projects "proj1" "proj2" "proj3")})
                        :provider-id "LPDAAC_ECS")])
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (vp/ingest-source-granule "LPDAAC_ECS"
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
               {"attribute[]" (format "string,%s,%s" svm/source-granule-ur-additional-attr-name
                                      granule-ur)
                :page-size 50}

               "Find virtual granule by shared fields"
               all-expected-granule-urs {:page-size 50 :project "proj1"})

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls]
        (vp/assert-matching-granule-urs
          [(svm/generate-granule-ur
             "LPDAAC_ECS" "AST_L1A" {:short-name (get-in vp-coll [:product :short-name])
                                     :version-id (get-in vp-coll [:product :version-id])} granule-ur)]
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))

    (testing "Update source granule"
      ;; Update the source granule so that the projects referenced are different than original
      (let [ast-l1a-gran-r2 (vp/ingest-source-granule "LPDAAC_ECS"
                                                      (assoc ast-l1a-gran
                                                             :project-refs ["proj2" "proj3"]
                                                             :revision-id nil))]
        (index/wait-until-indexed)
        (testing "find none by original project"
          (is (= 0 (:hits (search/find-refs :granule {:project "proj1"})))))

        (testing "Find virtual granule by shared fields"
          (vp/assert-matching-granule-urs
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
      (let [ast-l1a-gran-r4 (vp/ingest-source-granule "LPDAAC_ECS"
                                                      (dissoc ast-l1a-gran :revision-id :concept-id))]
        (index/wait-until-indexed)
        (testing "Find all granules"
          (vp/assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 50})))))))

(deftest all-granules-in-virtual-product-test
  (let [source-collections (vp/ingest-source-collections)
        ;; Ingest the virtual collections. For each virtual collection associate it with the source
        ;; collection to use later.
        vp-colls (reduce (fn [new-colls source-coll]
                           (into new-colls (map #(assoc % :source-collection source-coll)
                                                (vp/ingest-virtual-collections [source-coll]))))
                         []
                         source-collections)
        _ (index/wait-until-indexed)
        source-granules (doall (for [source-coll source-collections
                                     :let [{:keys [provider-id entry-title]} source-coll]
                                     granule-ur (svm/sample-source-granule-urs
                                                  [provider-id entry-title])]
                                 (vp/ingest-source-granule provider-id
                                                           (dg/granule source-coll {:granule-ur granule-ur}))))
        all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs source-granules)
                                         (map :granule-ur source-granules))]
    (index/wait-until-indexed)

    (testing "Find all granules"
      (vp/assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 1000})))

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls
              :let [{:keys [provider-id source-collection]} vp-coll
                    source-short-name (get-in source-collection [:product :short-name])
                    vp-short-name (get-in vp-coll [:product :short-name])
                    vp-version-id (get-in vp-coll [:product :version-id])]]
        (vp/assert-matching-granule-urs
          (map #(svm/generate-granule-ur provider-id source-short-name {:short-name vp-short-name
                                                                        :version-id vp-version-id} %)
               (svm/sample-source-granule-urs
                 [provider-id (:entry-title source-collection)]))
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 1000}))))))

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
    (is (:deleted (mdb/get-concept concept-id revision-id)))))

;; Verify that latest revision ids of virtual granules and the corresponding source granules
;; are in sync as various ingest operations are performed on the source granules
(deftest revision-ids-in-sync-test
  (let [[ast-coll] (vp/ingest-source-collections
                     [(assoc
                        (dc/collection
                          {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                           :short-name "AST_L1A"})
                        :provider-id "LPDAAC_ECS")])
        vp-colls (vp/ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (dg/granule ast-coll {:granule-ur granule-ur})
        ingest-result (vp/ingest-source-granule "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 5))
        _ (index/wait-until-indexed)
        vp-granule-ids (mapcat #(map :id (:refs (search/find-refs
                                                  :granule {:entry-title (:entry-title %)
                                                            :page-size 50}))) vp-colls)]

    ;; check revision ids are in sync after ingest/update operations
    (assert-virtual-gran-revision-id vp-colls 5)
    (vp/ingest-source-granule "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 10))
    (index/wait-until-indexed)
    (assert-virtual-gran-revision-id vp-colls 10)

    ;; check revision ids are in sync after delete operations
    (ingest/delete-concept (d/item->concept ingest-result) {:revision-id 12})
    (index/wait-until-indexed)
    (assert-tombstones vp-granule-ids 12)))

(deftest virtual-product-provider-alias-test
  (vp/with-provider-aliases
    {"LPDAAC_ECS"  #{"LP_ALIAS"}}
    (let [ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
          [ast-coll] (vp/ingest-source-collections
                       [(assoc
                          (dc/collection
                            {:entry-title ast-entry-title
                             :short-name "AST_L1A"})
                          :provider-id "LP_ALIAS")])
          vp-colls (vp/ingest-virtual-collections [ast-coll])
          granule-ur "SC:AST_L1A.003:2006227710"
          ast-l1a-gran (vp/ingest-source-granule "LP_ALIAS"
                                                 (dg/granule ast-coll {:granule-ur granule-ur}))
          expected-virtual-granule-urs (vp/source-granule->virtual-granule-urs
                                         (assoc ast-l1a-gran :provider-id "LPDAAC_ECS"))
          all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-virtual-granule-urs)]
      (index/wait-until-indexed)

      ;; Found all granules including the virtual granules
      (vp/assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 50}))

      ;; delete the source granule
      (ingest/delete-concept (d/item->concept ast-l1a-gran))
      (index/wait-until-indexed)

      ;; Found no granules, virtual granules are deleted as a result of deletion of source granule
      (vp/assert-matching-granule-urs
        []
        (search/find-refs :granule {:page-size 50})))))

(deftest virtual-product-non-cmr-only-provider-test
  (let [_ (ingest/update-ingest-provider {:provider-id "LPDAAC_ECS"
                                          :short-name "LPDAAC_ECS"
                                          :cmr-only false
                                          :small false})
        ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        [ast-coll] (vp/ingest-source-collections
                     [(assoc
                        (dc/collection
                          {:entry-title ast-entry-title
                           :short-name "AST_L1A"})
                        :provider-id "LPDAAC_ECS")] {:client-id "ECHO"})
        vp-colls (vp/ingest-virtual-collections [ast-coll] {:client-id "ECHO"})
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (vp/ingest-source-granule "LPDAAC_ECS"
                                               (dg/granule ast-coll {:granule-ur granule-ur})
                                               :client-id "ECHO")
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)
    (vp/assert-matching-granule-urs
      all-expected-granule-urs
      (search/find-refs :granule {:page-size 50}))))

(defn- get-virtual-granule-umms
  [src-granule-ur]
  (let [query-param {"attribute[]" (format "string,%s,%s"
                                           svm/source-granule-ur-additional-attr-name
                                           src-granule-ur)
                     :page-size 20}
        virt-gran-refs (:refs (search/find-refs :granule query-param))]
    (map #(-> % :id search/retrieve-concept :body g/parse-granule) virt-gran-refs)))

(deftest omi-aura-configuration-test
  (let [[omi-coll] (vp/ingest-source-collections
                    [(assoc
                      (dc/collection
                       {:entry-title (str "OMI/Aura Surface UVB Irradiance and Erythemal"
                                          " Dose Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd) at GES DISC")
                        :short-name "OMUVBd"})
                      :provider-id "GES_DISC")])
        vp-colls (vp/ingest-virtual-collections [omi-coll])
        granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2015m0103_v003-2015m0107t093002.he5"
        [ur-prefix ur-suffix] (str/split granule-ur #":")
        opendap-dir-path "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/"
        opendap-file-path (str opendap-dir-path granule-ur)]
    (util/are2 [src-granule-ur source-related-urls expected-related-url-maps]
               (let [_ (vp/ingest-source-granule
                        "GES_DISC"
                        (dg/granule
                         omi-coll {:granule-ur src-granule-ur
                                   :related-urls source-related-urls
                                   :data-granule {:day-night "UNSPECIFIED"
                                                  :production-date-time "2013-07-27T07:43:14.000Z"
                                                  :size 40}}))
                     _ (index/wait-until-indexed)
                     virt-gran-umm (first (get-virtual-granule-umms src-granule-ur))
                     expected-related-urls (map #(umm-c/map->RelatedURL %) expected-related-url-maps)]
                 (is (= (set expected-related-urls) (set (:related-urls virt-gran-umm))))
                 (is (nil? (get-in virt-gran-umm [:data-granule :size]))))

               "Related urls with only one access url which matches the pattern"
               granule-ur
               [{:url opendap-file-path :type "USE SERVICE API" :sub-type "OPENDAP DATA"}]
               [{:url (str opendap-file-path ".nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")
                 :type "USE SERVICE API"
                 :sub-type "OPENDAP DATA"
                 :title "(USE SERVICE API : OPENDAP DATA)"}]

               "Related urls with only one access url which matches the pattern, but is not
               an online resource url"
               granule-ur
               [{:url opendap-file-path :type "VIEW RELATED INFORMATION"}]
               ;; Only OPENDAP online resource url is kept in virtual granule
               nil

               "Multiple related urls"
               granule-ur
               [{:url opendap-file-path :type "USE SERVICE API" :sub-type "OPENDAP DATA" :mime-type "application/x-netcdf"}
                {:url "http://www.foo.com" :type "VIEW RELATED INFORMATION"}]
               [{:url (str opendap-file-path ".nc?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")
                 :type "USE SERVICE API"
                 :sub-type "OPENDAP DATA"
                 :title "(USE SERVICE API : OPENDAP DATA)"}])))

(deftest ast-granule-umm-matchers-test
  (vp/assert-psa-granules-match index/wait-until-indexed))

(def disabled-source-colls
  #{"OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd) at GES DISC"
    "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"})

(deftest virtual-products-disabled-source-collections-test
  (vp/with-disabled-source-collections
    disabled-source-colls
    (let [source-collections (vp/ingest-source-collections)
          enabled-source-collections (remove #(contains? disabled-source-colls (:entry-title %))
                                             source-collections)
          disabled-source-collections (filter #(contains? disabled-source-colls (:entry-title %))
                                              source-collections)
          ingest-colls-fn (fn [new-colls source-coll]
                            (into new-colls (map #(assoc % :source-collection source-coll)
                                                 (vp/ingest-virtual-collections [source-coll]))))
          enabled-vp-colls (reduce ingest-colls-fn
                                   []
                                   enabled-source-collections)
          disabled-vp-colls (reduce ingest-colls-fn
                                    []
                                    disabled-source-collections)
          _ (index/wait-until-indexed)
          source-granules (doall (for [source-coll source-collections
                                       :let [{:keys [provider-id entry-title]} source-coll]
                                       granule-ur (svm/sample-source-granule-urs
                                                    [provider-id entry-title])]
                                   (vp/ingest-source-granule provider-id
                                                             (dg/granule source-coll {:granule-ur granule-ur}))))
          expected-source-granules (remove #(contains? disabled-source-colls
                                                       (get-in % [:collection-ref :entry-title]))
                                           source-granules)
          all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs expected-source-granules)
                                           (map :granule-ur source-granules))]
      (index/wait-until-indexed)

      (testing "Find all granules"
        (vp/assert-matching-granule-urs
          all-expected-granule-urs
          (search/find-refs :granule {:page-size 1000})))

      (testing "Find all granules in enabled virtual collections"
        (doseq [vp-coll enabled-vp-colls
                :let [{:keys [provider-id source-collection]} vp-coll
                      source-short-name (get-in source-collection [:product :short-name])
                      vp-short-name (get-in vp-coll [:product :short-name])
                      vp-version-id (get-in vp-coll [:product :version-id])]]
          (vp/assert-matching-granule-urs
            (map #(svm/generate-granule-ur provider-id source-short-name {:short-name vp-short-name
                                                                          :version-id vp-version-id} %)
                 (svm/sample-source-granule-urs
                   [provider-id (:entry-title source-collection)]))
            (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                        :page-size 1000}))))

      (testing "Find all granules in disabled virtual collections"
        (doseq [vp-coll disabled-vp-colls]
          (vp/assert-matching-granule-urs
            []
            (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                        :page-size 1000})))))))

(deftest CMR-5848-umm-json-granule-virtual-product-ingest-test
  (testing "ingest with Virtual-Product-Service with a umm-json granule"
    (let [[ast-coll] (vp/ingest-source-collections
                       [(assoc
                          (dc/collection
                            {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                             :short-name "AST_L1A"
                             :projects (dc/projects "proj1" "proj2" "proj3" umm-spec-util/not-provided)})
                          :provider-id "LPDAAC_ECS")])
          vp-colls (vp/ingest-virtual-collections [ast-coll])
          granule-ur "SC:AST_L1A.003:2006227720"
          granule (vp/add-granule-attributes "LPDAAC_ECS"
                                              (dg/granule ast-coll {:granule-ur granule-ur
                                                                    :provider-id "LPDAAC_ECS"
                                                                    :project-refs ["proj1"]}))

          concept (d/item->concept granule {:format :umm-json
                                            :version "1.6"})
          _ (ingest/ingest-concept concept)
          expected-granule-urs (vp/source-granule->virtual-granule-urs granule)
          all-expected-granule-urs (cons (:granule-ur granule) expected-granule-urs)]
      (index/wait-until-indexed)
      (vp/assert-matching-granule-urs
       all-expected-granule-urs
       (search/find-refs :granule {:page-size 50})))))
