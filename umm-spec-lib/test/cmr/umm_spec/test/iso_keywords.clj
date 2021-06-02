(ns cmr.umm-spec.test.iso-keywords
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.iso-keywords :as iso-keywords]))

(deftest science-keyword-empty-value-test
  (testing "Empty Topic is rejected when `sanitize?` is false"
    (let [cmr-6840-example-collection (-> "example-data/special-case-files/CMR-6840-empty-facet-titles.xml"
                                          io/resource
                                          io/file
                                          slurp)]
      ;; Parse function should return nil - it is throwing a service error that
      ;; that we do not catch in this unit test
      (is (= nil (iso-keywords/parse-science-keywords cmr-6840-example-collection false))))))

(deftest science-keyword-wmo-keyword-gco-gmx-mixed-test
  (testing "WMO keyword is not translated to ScienceKeyword, both gco and gmx values are counted."
    (let [cmr-7250-coll (-> "example-data/special-case-files/CMR-7250-Mix-WMO-And-ScienceKeywords.xml"
                                          io/resource
                                          io/file
                                          slurp)
          md-data-id-base-xpath "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification"
          md-data-id-el (first (select cmr-7250-coll md-data-id-base-xpath))
          keyword-type iso-keywords/science-keyword-type
          keyword-title iso-keywords/science-keyword-title
          ;; Verify skw-nottheme-oceanography not included(right title, wrong type.);
          ;;        wkw-oceanography not included(right type, wrong title - WMO keyword);
          ;;        EARTH SCIENCE gmxAnchor and skw-oceanography are included (gmx:Anchor cases)
          ;;        Six other EARTH SCIENCE are included (gco:CharacterString cases).
          expected-skw ["EARTH SCIENCE > BIOLOGICAL CLASSIFICATION > ANIMALS/VERTEBRATES > BIRDS > NONE > NONE > NONE"
                        "EARTH SCIENCE > BIOSPHERE > AQUATIC ECOSYSTEMS > LAKES > NONE > NONE > NONE"
                        "EARTH SCIENCE > BIOSPHERE > AQUATIC ECOSYSTEMS > LAKES > SALINE LAKES > NONE > NONE"
                        "EARTH SCIENCE > BIOSPHERE > AQUATIC ECOSYSTEMS > MARINE HABITAT > NONE > NONE > NONE"
                        "EARTH SCIENCE > BIOSPHERE > ECOLOGICAL DYNAMICS > SPECIES/POPULATION INTERACTIONS > MIGRATORY RATES/ROUTES > NONE > NONE"
                        "EARTH SCIENCE > BIOSPHERE > ECOLOGICAL DYNAMICS > SPECIES/POPULATION INTERACTIONS > POPULATION DYNAMICS > NONE > NONE"
                        "EARTH SCIENCE gmxAnchor > BIOSPHERE > ECOLOGICAL DYNAMICS > ECOTOXICOLOGY > TOXICITY LEVELS > NONE > NONE"
                        "skw-oceanography"]]
      ;; descriptive-keywords-with-title function should return the right number of ScienceKeywords 
      (is (= expected-skw (iso-keywords/descriptive-keywords-with-title md-data-id-el keyword-type keyword-title))))))
