(ns cmr.system-int-test.search.collection-facets-search-test
  "This tests the retrieving facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(comment

  (ingest/reset)
  (ingest/create-provider "provguid1" "PROV1")
  (ingest/create-provider "provguid2" "PROV2")
  )


;; TODO document facets on the API
;; campaigns (called projects on API) - multiple per collection
;; platforms, instruments, and sensors - multiple per collection
;; twod coord names - multiple per collection
;; science keywords - multiple per collection
;; processing levels - 1 per collection

;; TODO test spatial search with this. It fails in ECHO: 11014513

;; TODO add tests where searching on things that are faceted should narrow down the results found
;; See NCR 11014514
;; - may want to manually test this in elasticsearch on SIT

(defn make-coll
  "Helper for creating and ingesting a collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

;; Attrib functions - These are helpers for creating maps with collection attributes
(defn projects
  [& project-names]
  {:projects (apply dc/projects project-names)})

(defn platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (apply dc/platform platform-name nil
             (for [in (range 0 num-instruments)
                   :let [instrument-name (str platform-name "-i" in)]]
               (apply dc/instrument instrument-name nil
                      (for [sn (range 0 num-sensors)
                            :let [sensor-name (str instrument-name "-s" sn)]]
                        (dc/sensor sensor-name))))))}))

(defn twod-coords
  [& names]
  {:two-d-coordinate-systems (map dc/two-d names)})


(def sk1 (dc/science-keyword {:category "Cat1"
                              :topic "Topic1"
                              :term "Term1"
                              :variable-level-1 "Level1-1"
                              :variable-level-2 "Level1-2"
                              :variable-level-3 "Level1-3"
                              :detailed-variable "Detail1"}))


(def sk2 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "Extreme"
                              :variable-level-1 "Level2-1"
                              :variable-level-2 "Level2-2"
                              :variable-level-3 "Level2-3"
                              :detailed-variable "UNIVERSAL"}))

(def sk3 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "UNIVERSAL"}))

(def sk4 (dc/science-keyword {:category "Hurricane"
                              :topic "Cool"
                              :term "Term4"
                              :variable-level-1 "UNIVERSAL"}))

(def sk5 (dc/science-keyword {:category "Tornado"
                              :topic "Popular"
                              :term "Extreme"}))

(def sk6 (dc/science-keyword {:category "UPCASE"
                              :topic "Popular"
                              :term "Mild"}))

(def sk7 (dc/science-keyword {:category "upcase"
                              :topic "Cool"
                              :term "Mild"}))

(defn science-keywords
  [& sks]
  {:science-keywords sks})

(defn processing-level-id
  [id]
  {:processing-level-id id})

;; TODO test invalid facets search cases


(deftest retrieve-collection-facets-test
  (let [coll1 (make-coll 1 "PROV1"
                         (projects "proj1" "PROJ2")
                         (platforms "A" 2 2 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1 sk4 sk5)
                         (processing-level-id "PL1"))
        coll2 (make-coll 2 "PROV2"
                         (projects "proj3" "PROJ2")
                         (platforms "B" 2 2 1)
                         (science-keywords sk1 sk2 sk3)
                         (processing-level-id "pl1"))
        coll3 (make-coll 3 "PROV2"
                         (platforms "A" 1 1 1)
                         (twod-coords "Alpha" "Bravo")
                         (science-keywords sk5 sk6 sk7)
                         (processing-level-id "PL1"))
        coll4 (make-coll 4 "PROV1"
                         (twod-coords "alpha")
                         (science-keywords sk3)
                         (processing-level-id "PL2"))
        coll5 (make-coll 5 "PROV2")
        all-colls [coll1 coll2 coll3 coll4 coll5]]

    (index/refresh-elastic-index)

    (testing "retreving all facets in different formats"
      (let [expected-facets [{:field "project"
                              :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                             {:field "platform"
                              :value-counts [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                             {:field "instrument"
                              :value-counts [["A-p0-i0" 2]
                                             ["A-p0-i1" 1]
                                             ["A-p1-i0" 1]
                                             ["A-p1-i1" 1]
                                             ["B-p0-i0" 1]
                                             ["B-p0-i1" 1]
                                             ["B-p1-i0" 1]
                                             ["B-p1-i1" 1]]}
                             {:field "sensor"
                              :value-counts [["A-p0-i0-s0" 2]
                                             ["A-p0-i1-s0" 1]
                                             ["A-p1-i0-s0" 1]
                                             ["A-p1-i1-s0" 1]
                                             ["B-p0-i0-s0" 1]
                                             ["B-p0-i1-s0" 1]
                                             ["B-p1-i0-s0" 1]
                                             ["B-p1-i1-s0" 1]]}
                             {:field "two_d_coordinate_system_name"
                              :value-counts [["Alpha" 2] ["Bravo" 1] ["alpha" 1]]}
                             {:field "processing_level_id"
                              :value-counts [["PL1" 2] ["PL2" 1] ["pl1" 1]]}
                             {:field "category"
                              :value-counts [["Hurricane" 4]
                                             ["Cat1" 2]
                                             ["Tornado" 2]
                                             ["UPCASE" 1]
                                             ["upcase" 1]]}
                             {:field "topic"
                              :value-counts [["Popular" 6] ["Cool" 2] ["Topic1" 2]]}
                             {:field "term"
                              :value-counts [["Extreme" 3] ["Mild" 2] ["Term1" 2] ["UNIVERSAL" 2] ["Term4" 1]]}
                             {:field "variable_level_1"
                              :value-counts [["Level1-1" 2] ["Level2-1" 1] ["UNIVERSAL" 1]]}
                             {:field "variable_level_2"
                              :value-counts [["Level1-2" 2] ["Level2-2" 1]]}
                             {:field "variable_level_3"
                              :value-counts [["Level1-3" 2] ["Level2-3" 1]]}
                             {:field "detailed_variable"
                              :value-counts [["Detail1" 2] ["UNIVERSAL" 1]]}]]
        (testing "refs"
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true})))))
        (testing "metadata items and direct transformer"
          (is (= expected-facets
                 (:facets (search/find-metadata :collection
                                                :echo10
                                                {:include-facets true
                                                 :concept-id (map :concept-id all-colls)})))))))

    ;; TODO ensure ACLs apply to search facets.
    ;; Will want to disable automatic granting of everything and specifically add acls for some collections
    ;; have a collection with many fields. Make sure the counts don't include the things from that collection.
    ;; Can write this test after the others and it won't impact what the other ones are testing.
    ;; Code wise internally everything should stay the same.

    ;; TODO refs
    ;; TODO atom
    ;; TODO json
    ;; TODO metadata
    ;; TODO test search parameters apply to the facets found




    ))



