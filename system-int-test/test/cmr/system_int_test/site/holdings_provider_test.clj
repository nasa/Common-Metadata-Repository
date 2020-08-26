(ns cmr.system-int-test.site.holdings-provider-test
  (:require [clojure.test :refer :all]
            [cmr.mock-echo.client.echo-util :as echo]
            [cmr.system-int-test.data2.core :as data]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
            [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
            [cmr.system-int-test.system :as system]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.tag-util :as tags]
            [crouton.html :as html]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"}
                                             {:grant-all-ingest? true
                                              :grant-all-search? true
                                              :grant-all-access-control? false})
                       tags/grant-all-tag-fixture]))

(defn find-element-by-type
  [e-type dom-node]
  (flatten (lazy-cat (when (= e-type (:tag dom-node))
                       [dom-node])
                     (when-let [children (:content dom-node)]
                       (map (partial find-element-by-type e-type)
                            children)))))

(defn find-element-by-id
  [id dom-node]
  (let [tree (lazy-cat (when (= id (get-in dom-node [:attrs :id]))
                         [dom-node])
                       (when-let [children (:content dom-node)]
                         (map (partial find-element-by-id id)
                              children)))]
    (->> tree
         flatten
         (remove nil?)
         first)))

(deftest provider-holdings-test
  (let [coll1 (data/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection {:concept-id "C1-PROV1"
                                        :TemporalExtents
                                        [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        coll2 (data/ingest-umm-spec-collection
                "PROV1" (data-umm-c/collection
                          {:concept-id "C2-PROV1"
                           :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                           :EntryTitle "C2-PROV1-et"
                           :ShortName "C2-PROV1-sn"}))
        _ (index/wait-until-indexed)

        _g1 (data/ingest "PROV1" (dg/granule-with-umm-spec-collection
                                   coll1 (:concept-id coll1)
                                   {:granule-ur "Granule1"
                                    :beginning-date-time "1970-06-02T12:00:00Z"
                                    :ending-date-time "1975-02-02T12:00:00Z"}))

        _g2 (data/ingest "PROV1"
                         (dg/granule-with-umm-spec-collection
                           coll2 (:concept-id coll2)
                           {:spatial-coverage (dg/spatial-with-track
                                                {:cycle 1
                                                 :passes [{:pass 1}]})
                            :beginning-date-time "2012-01-01T00:00:00.000Z"
                            :ending-date-time "2012-01-01T00:00:00.000Z"})
                         {:format :umm-json})

        _g3 (data/ingest "PROV1"
                         (dg/granule-with-umm-spec-collection
                           coll2 (:concept-id coll2)
                           {:spatial-coverage (dg/spatial-with-track
                                                {:cycle 2
                                                 :passes [{:pass 3}
                                                          {:pass 4}]})
                            :beginning-date-time "2012-01-01T00:00:00.000Z"
                            :ending-date-time "2012-01-01T00:00:00.000Z"})
                         {:format :umm-json})

        _ (index/wait-until-indexed)
        user1-token (echo/login (system/context) "user1")
        tag1-colls [coll1 coll2]
        tag-key "tag1"]

    (tags/save-tag
      user1-token
      (tags/make-tag {:tag-key tag-key})
      tag1-colls)
    (index/wait-until-indexed)

    (testing "virtual directory links exist"
      (let [page-data (html/parse "http://localhost:3003/site/collections/directory/PROV1/tag1")]
        (is (= 2
               (->> page-data
                    (find-element-by-type :a)
                    (filter #(re-matches #".*virtual-directory/C\d-PROV\d.*"
                                         (get-in % [:attrs :href])))
                    count)))))))

