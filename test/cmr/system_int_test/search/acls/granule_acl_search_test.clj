(ns cmr.system-int-test.search.acls.granule-acl-search-test
  "Tests searching for collections with ACLs in place"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.common.services.messages :as msg]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.echo-util :as e]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"
                                           "provguid4" "PROV4"} false))

(def coll-num (atom 0))
(def gran-num (atom 0))

(defn make-coll
  ([prov]
   (make-coll prov {}))
  ([prov attribs]
   (let [n (swap! coll-num inc)]
     (d/ingest prov
               (dc/collection
                 (merge {:entry-title (str "coll" n)}
                        attribs))))))

(defn make-gran
  ([coll]
   (make-gran coll {}))
  ([coll attribs]
   (let [prov (:provider-id coll)
         n (swap! gran-num inc)
         attribs (merge {:granule-ur (str "gran" n)}
                        attribs)]
     (d/ingest prov (dg/granule coll attribs)))))

(comment
  (do
    (ingest/reset)
    (ingest/create-provider "provguid1" "PROV1" false)
    (ingest/create-provider "provguid2" "PROV2" false)
    (ingest/create-provider "provguid3" "PROV3" false)
    (ingest/create-provider "provguid4" "PROV4" false))
  )

(deftest granule-search-with-acls-test
  ;; TODO add acls for collection entry title that doesn't exist
  ;; TODO test a search with no acls applied

  (do
    (reset! coll-num 0)
    (reset! gran-num 0)

    ;; Guests have access to coll1
    (e/grant-guest (e/gran-catalog-item-id "provguid1" (e/coll-id ["coll1"])))
    ;; coll 2 has no granule permissions
    ;; Permits granules with access values.
    (e/grant-guest (e/gran-catalog-item-id "provguid1" nil (e/gran-id {:min-value 10
                                                                       :max-value 20
                                                                       :include-undefined true})))
    (e/grant-registered-users
      (e/gran-catalog-item-id "provguid2" (e/coll-id ["coll3"] {:min-value 1 :max-value 3})))
    (e/grant-registered-users
      (e/gran-catalog-item-id "provguid2" (e/coll-id [] {:min-value 4 :max-value 6})))

    ;; Guests have full access to provider 3
    (e/grant-guest (e/gran-catalog-item-id "provguid3"))
    ;; Clear out acl caches
    (ingest/clear-caches))

  (let [coll1 (make-coll "PROV1")
        coll2 (make-coll "PROV1")
        coll3 (make-coll "PROV2" {:access-value 2})
        coll4 (make-coll "PROV2" {:access-value 5})
        coll5 (make-coll "PROV3")
        coll6 (make-coll "PROV4")

        ;; - PROV1 -
        gran1 (make-gran coll1)
        gran2 (make-gran coll1)

        ;; Permitted through undefined access value
        gran3 (make-gran coll2)
        ; Not permitted at all (outside of access value range)
        gran4 (make-gran coll2 {:access-value 9})
        ;; Permitted through access value range
        gran5 (make-gran coll2 {:access-value 10})

        ;; - PROV2 -
        ;; Permitted by collection id and coll access value
        gran6 (make-gran coll3)
        ;; Permitted by collection 4's access value
        gran7 (make-gran coll4)

        ;; - PROV3 -
        ;; All granules in prov 3 are permitted
        gran8 (make-gran coll5)
        gran9 (make-gran coll5 {:access-value 0})

        ;; - PROV4 - no permitted access
        gran10 (make-gran coll6)
        guest-token (e/login-guest)
        user1-token (e/login "user1")]

    (index/refresh-elastic-index)

    (testing "search as guest"
      (let [refs-result (search/find-refs :granule {:token guest-token})
            expected [gran1 gran2 gran3 gran5 gran8 gran9]
            match? (d/refs-match? expected refs-result)]
        (when-not match?
          (println "Expected:" (map :concept-id expected))
          (println "Actual:" (map :id (:refs refs-result)))
          (println "Expected:" (map :granule-ur expected))
          (println "Actual:" (map :name (:refs refs-result))))
        (is match?)))

    #_(testing "search as registered users"
        (is (d/refs-match? [gran6 gran7]
                           (search/find-refs :granule {:token user1-token}))))

    ;; TODO test searching
    ;; with no collection concept ids
    ;; 1 coll concept id
    ;; 2 coll concept ids
    ;; a provider id
    ;; a provider id and entry title
    ;; an entry title


    ))