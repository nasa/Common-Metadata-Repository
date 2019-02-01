(ns cmr.system-int-test.virtual-product.translation-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.virtual-product-util :as virtual-product-util]
   [cmr.virtual-product.config]
   [cmr.virtual-product.data.source-to-virtual-mapping :as source-to-virtual-mapping]))

(use-fixtures :each (ingest/reset-fixture (into {"PROV_guid" "PROV"
                                                 "LP_ALIAS_guid" "LP_ALIAS"}
                                                (for [p virtual-product-util/virtual-product-providers]
                                                  [(str p "_guid") p]))))


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
  (virtual-product-util/with-provider-aliases
   {"LPDAAC_ECS"  #{"LP_ALIAS"}}
   (let [ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
         [ast-coll] (virtual-product-util/ingest-source-collections
                     [(assoc
                       (collection/collection
                        {:entry-title ast-entry-title
                         :short-name "AST_L1A"})
                       :provider-id "LPDAAC_ECS")])
         [alias-ast-coll] (virtual-product-util/ingest-source-collections
                           [(assoc
                             (collection/collection
                              {:entry-title ast-entry-title
                               :short-name "AST_L1A"})
                             :provider-id "LP_ALIAS")])
         vp-colls (virtual-product-util/ingest-virtual-collections [ast-coll])
         alias-vp-colls (virtual-product-util/ingest-virtual-collections [alias-ast-coll])
         ast-gran (virtual-product-util/ingest-source-granule
                   "LPDAAC_ECS"
                   (granule/granule
                    ast-coll
                    {:granule-ur "SC:AST_L1A.003:2006227720"}))
         alias-ast-gran (virtual-product-util/ingest-source-granule
                         "LP_ALIAS"
                         (granule/granule
                          alias-ast-coll
                          {:granule-ur "SC:AST_L1A.003:2006227720"}))
         prov-ast-coll (data-core/ingest "PROV"
                                         (collection/collection
                                          {:entry-title ast-entry-title}))
         prov-ast-gran (data-core/ingest "PROV"
                                         (granule/granule
                                          prov-ast-coll
                                          {:granule-ur "SC:AST_L1A.003:2006227720"}))

         lpdaac-non-ast-coll (data-core/ingest "LPDAAC_ECS"
                                               (collection/collection
                                                {:entry-title "non virtual entry title"}))
         lpdaac-non-ast-gran (data-core/ingest "LPDAAC_ECS"
                                               (granule/granule
                                                lpdaac-non-ast-coll
                                                {:granule-ur "granule-ur2"}))
         prov-coll (data-core/ingest "PROV"
                                     (collection/collection
                                      {:entry-title "some other entry title"}))
         prov-gran1 (data-core/ingest "PROV" (granule/granule
                                              prov-coll
                                              {:granule-ur "granule-ur3"}))

         prov-gran2 (data-core/ingest "PROV" (granule/granule
                                              prov-coll
                                              {:granule-ur "granule-ur4"}))


         _ (index/wait-until-indexed)
         [source-granule alias-source-granule]   (map granule->entry [ast-gran alias-ast-gran])
         virtual-granule1 (get-granule-entry-triplet (first vp-colls))
         virtual-granule2 (get-granule-entry-triplet (second vp-colls))
         virtual-granule3 (get-granule-entry-triplet (nth vp-colls 2))
         virtual-granule4 (get-granule-entry-triplet (nth vp-colls 3))
         alias-virtual-granule1 (get-granule-entry-triplet (first alias-vp-colls))

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
         non-virtual-granule4 (granule->entry prov-gran2)

         ;; A collection entry
         ast-coll-entry (assoc (select-keys ast-coll [:entry-title :concept-id]) :granule-ur  nil)]

     (testing "Valid input to translate-granule-entries end-point"
       (util/are2 [request-json expected-response-json]
         (let [response (virtual-product-util/translate-granule-entries
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
         [non-virtual-granule1 virtual-granule1 alias-virtual-granule1]
         [non-virtual-granule1 source-granule alias-source-granule]

         "The order of the output granules should match the corresponding input. Duplicates
                   in the source should be preserved."
         [source-granule non-virtual-granule1 virtual-granule1 non-virtual-granule2
          non-virtual-granule3 non-virtual-granule4 virtual-granule2 virtual-granule3
          non-virtual-granule3 non-virtual-granule3 virtual-granule4 source-granule
          non-virtual-granule1 virtual-granule1]
         [source-granule non-virtual-granule1 source-granule non-virtual-granule2
          non-virtual-granule3 non-virtual-granule4 source-granule source-granule
          non-virtual-granule3 non-virtual-granule3 source-granule source-granule
          non-virtual-granule1 source-granule]

         "Collection entries should not undergo any translation"
         [virtual-granule1 non-virtual-granule1 ast-coll-entry source-granule]
         [source-granule non-virtual-granule1 ast-coll-entry source-granule]))

     (testing "Translating a granule which is deleted"
       (util/are2 [deleted-granule request-json expected-response-json]
         (let [_ (ingest/delete-concept (data-core/item->concept deleted-granule))
               _ (index/wait-until-indexed)
               response (virtual-product-util/translate-granule-entries
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
       (let [malformed-json (string/replace (json/generate-string [virtual-granule1]) #"}" "]")
             response (virtual-product-util/translate-granule-entries malformed-json)
             errors (:errors (json/parse-string (:body response) true))]
         (is (= 400 (:status response)))
         (is (= 1 (count errors)))
         (is (string/starts-with? (first errors) "Invalid JSON: Expected a ',' or '}' at"))))

     (testing "Invalid input to translate-granule-items end-point should result in error"
       (let [invalid-json (json/generate-string [virtual-granule1
                                                 (dissoc virtual-granule1 :concept-id)])
             response (virtual-product-util/translate-granule-entries invalid-json)
             errors (:errors (json/parse-string (:body response) true))]
         (is (= 400 (:status response)))
         (is (= ["#/1: required key [concept-id] not found"] errors))))

     (testing "Empty granule entries should result in error"
       (let [response (virtual-product-util/translate-granule-entries
                       (json/generate-string []))
             errors (:errors (json/parse-string (:body response) true))]
         (is (= 400 (:status response)))
         (is (= ["#: expected minimum item count: 1, found: 0"]
                errors))))

     (testing "More than 2000 granule entries in a request results in error"
       (let [granule-entry (fn [i]
                             {:concept-id (format "G1000%s-PROV" i)
                              :entry-title "Dataset1"
                              :granule-ur (str "gran" i)})
             granule-entries (map granule-entry (range 2001))
             response (virtual-product-util/translate-granule-entries
                       (json/generate-string granule-entries))
             errors (:errors (json/parse-string (:body response) true))]
         (is (= 400 (:status response)))
         (is (= ["The maximum allowed granule entries in a request is 2000, but was 2001."]
                errors))))

     (testing "If virtual products is disabled, there should be no translation"
       (try (dev-sys-util/eval-in-dev-sys
             `(cmr.virtual-product.config/set-virtual-products-enabled! false))
         (let [granule-entries [source-granule non-virtual-granule1 virtual-granule1 non-virtual-granule2
                                non-virtual-granule3 non-virtual-granule4 virtual-granule2 virtual-granule3]
               response (virtual-product-util/translate-granule-entries
                         (json/generate-string granule-entries))]
           (= granule-entries (json/parse-string (:body response) true)))
         (finally (dev-sys-util/eval-in-dev-sys
                   `(cmr.virtual-product.config/set-virtual-products-enabled! true))))))))

(defn- get-virtual-entries-by-source
  "Build virtual entries by source granule umm"
  [src-umm]
  (let [src-granule-ur (:granule-ur src-umm)
        provider-id (:provider-id src-umm)
        entry-title (get-in src-umm [:collection-ref :entry-title])
        attr-search-str (format "string,%s,%s"
                                source-to-virtual-mapping/source-granule-ur-additional-attr-name
                                src-granule-ur)]
    (flatten
      (for [virt-coll (:virtual-collections (get source-to-virtual-mapping/source-to-virtual-product-mapping
                                                 [provider-id entry-title]))
            :let [virt-entry-title (:entry-title virt-coll)
                  granule-refs (:refs (search/find-refs
                                        :granule
                                        {"attribute[]" attr-search-str
                                         :entry-title virt-entry-title
                                         :page-size 1}))]]
        (for [gran-ref granule-refs
              :let [{virt-granule-id :id virt-granule-ur :name} gran-ref]]
          {:entry-title virt-entry-title
           :concept-id virt-granule-id
           :granule-ur virt-granule-ur})))))


;; This test tests the tranlsation end-point using all the sample granule-urs defined for virtual
;; granules in the config file. All the virtual granule entries should be translated to corresponding
;; source entries by the end-point.
(deftest all-virtual-granules-translate-entries-test
  (let [source-collections (virtual-product-util/ingest-source-collections)
        ;; Ingest the virtual collections. For each virtual collection associate it with the source
        ;; collection to use later.
        vp-colls (reduce (fn [new-colls source-coll]
                           (into new-colls (map #(assoc % :source-collection source-coll)
                                                (virtual-product-util/ingest-virtual-collections
                                                 [source-coll]))))
                         []
                         source-collections)
        _ (index/wait-until-indexed)
        src-grans (doall (for [source-coll source-collections
                               :let [{:keys [provider-id entry-title]} source-coll]
                               granule-ur (source-to-virtual-mapping/sample-source-granule-urs
                                            [provider-id entry-title])]
                           (virtual-product-util/ingest-source-granule
                            provider-id
                            (granule/granule
                             source-coll
                             {:granule-ur granule-ur}))))
        _ (index/wait-until-indexed)
        virt-gran-entries-by-src (into {} (map #(vector % (get-virtual-entries-by-source %)) src-grans))
        all-virt-entries (mapcat second virt-gran-entries-by-src)
        expected-src-entries (mapcat
                               #(repeat (count (second %)) (granule->entry (first %)))
                               virt-gran-entries-by-src)]

    (let [response (virtual-product-util/translate-granule-entries
                     (json/generate-string all-virt-entries))]
      (= expected-src-entries (json/parse-string (:body response) true)))))

(defn- make-grans
  "Ingest n granules for the given collection"
  [coll n]
  (doall (for [i (range 0 n)]
           (data-core/ingest "PROV"
                             (granule/granule
                              coll
                              {:granule-ur (str "SC:MIL2ASAE.002:2505203" i)})))))

;; This test is added for CMR-3508 to show that we can now handle translation of more than
;; 10 (default search page size) granules on a single collection during the translation
(deftest translate-granule-entries-more-than-default-page-size-test
  (let [coll (data-core/ingest "PROV"
                               (collection/collection {:entry-title "MISR Level 2 Aerosol parameters V002"}))
        grans (make-grans coll 12)
        gran->entry (fn [g]
                      {:concept-id (:concept-id g)
                       :entry-title "MISR Level 2 Aerosol parameters V002"
                       :granule-ur (:granule-ur g)})
        entries (map gran->entry grans)
        _ (index/wait-until-indexed)
        response (virtual-product-util/translate-granule-entries
                  (json/generate-string entries))]
    (is (= entries (json/parse-string (:body response) true)))))
