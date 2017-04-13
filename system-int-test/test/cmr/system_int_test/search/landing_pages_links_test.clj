(ns cmr.system-int-test.search.landing-pages-links-test
  "This tests searching by tags to generate links for landing pages"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :as util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.umm-spec.models.umm-common-models :as cm]
            [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"
                                              "provguid3" "PROV3"})
                       tags/grant-all-tag-fixture]))

;; XXX Add supporting functions for generating HTML links in the expected
;; format
; (defn gen-doi-link
;   ""
;   [doi-data]
;   )

; (defn gen-cmr-link
;   ""
;   [provider concept-id]
;   )

(defn setup-providers
  ""
  []
  (let [p1 {:provider-id "PROV1" :short-name "S1" :cmr-only true :small true}
        p2 {:provider-id "PROV2" :short-name "S2" :cmr-only true :small true}
        p3 {:provider-id "PROV3" :short-name "S3" :cmr-only true :small true}]
    (map ingest/create-provider [p1 p2 p3])))

(defn setup-collections
  "A utility function that generates testing collections data with the bits we
  need to test."
  []
  (let [[c1-p1 c2-p1 c3-p1
         c1-p2 c2-p2 c3-p2] (for [p ["PROV1" "PROV2"]
                                  n (range 1 4)]
                              (d/ingest-umm-spec-collection
                                p
                                (-> exp-conv/example-collection-record
                                    (assoc :ShortName (str "s" n))
                                    (assoc :EntryTitle (str "coll" n)))
                                {:format :umm-json
                                 :accept-format :json}))
         [c1-p3 c2-p3 c3-p3] (for [n (range 4 7)]
                               (d/ingest-umm-spec-collection
                                "PROV3"
                                (-> exp-conv/example-collection-record
                                    (assoc :ShortName (str "s" n))
                                    (assoc :EntryTitle (str "coll" n))
                                    (assoc :DOI (cm/map->DoiType
                                                  {:DOI (str "doi" n)
                                                   :Authority (str "auth" n)})))
                                {:format :umm-json
                                 :accept-format :json}))]
    ;; Wait until collections are indexed so tags can be associated with them
    (index/wait-until-indexed)
    ;; Use the following to generate html links that will be matched in tests
    (let [user-token (e/login (s/context) "user")
          notag-colls [c1-p1 c1-p2 c1-p3]
          nodoi-colls [c1-p1 c2-p1 c3-p1 c1-p2 c2-p2 c3-p2]
          doi-colls [c1-p3 c2-p3 c3-p3]
          all-colls (into nodoi-colls doi-colls)
          tag-colls [c2-p1 c2-p2 c2-p3 c3-p1 c3-p2 c3-p3]
          tag (tags/save-tag
                user-token
                (tags/make-tag {:tag-key "gov.nasa.eosdis"})
                tag-colls)]
    ; (tags/associate-by-concept-ids
    ;   user-token
    ;   "gov.nasa.eosdis"
    ;   [{:concept-id (:concept-id tag-colls)
    ;     :revision-id (:revision-id tag-colls)
    ;     :data "stuff"}])
    (index/wait-until-indexed)
    [notag-colls nodoi-colls doi-colls tag-colls all-colls])))

(deftest get-eosdis-landing-links
  (testing "generate links from gov.nasa.eosdis-tagged collection data"
    (setup-providers)
    (let [[notag-colls nodoi-colls doi-colls tag-colls all-colls] (setup-collections)]
      ;; XXX test that items in the notag-colls don't have links
      ;; XXX test that only items in the doi-colls have doi links
      ;; XXX test that only items in tag-colls have links
      ;; XXX test that only one provider is present in a provider links page
      (is (= (count notag-colls) 3))
      (is (= (count nodoi-colls) 6))
      (is (= (count doi-colls) 3))
      (is (= (count tag-colls) 6))
      (is (= (count all-colls) 9)))))
