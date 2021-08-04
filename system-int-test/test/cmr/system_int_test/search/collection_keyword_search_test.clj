(ns cmr.system-int-test.search.collection-keyword-search-test
  "Integration test for CMR collection search by keyword terms"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.search.data.keywords-to-elastic :as k2e]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.models.umm-collection-models :as um]
    [cmr.umm-spec.models.umm-common-models :as cm]
    [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3" "provguid4" "PROV4" "provguid5" "PROV5"}))

(deftest search-by-keywords
  (let [short-name-boost (k2e/get-boost nil :short-name)
        entry-id-boost (k2e/get-boost nil :entry-id)
        project-boost (k2e/get-boost nil :project)
        platform-boost (k2e/get-boost nil :platform)
        instrument-boost (k2e/get-boost nil :instrument)
        concept-id-boost (k2e/get-boost nil :concept-id)
        provider-boost (k2e/get-boost nil :provider)
        entry-title-boost (k2e/get-boost nil :entry-title)
        two-d-boost (k2e/get-boost nil :two-d-coord-name)
        processing-level-boost (k2e/get-boost nil :processing-level-id)
        science-keywords-boost (k2e/get-boost nil :science-keywords)
        data-center-boost (k2e/get-boost nil :data-center)
        psa1 (data-umm-cmn/additional-attribute {:Name "alpha" :DataType "STRING" :Value "ab"})
        psa2 (data-umm-cmn/additional-attribute {:Name "bravo" :DataType "STRING" :Value "bf"})
        psa3 (data-umm-cmn/additional-attribute {:Name "charlie" :DataType "STRING" :Value "foo"})
        psa4 (data-umm-cmn/additional-attribute {:Name "case" :DataType "STRING" :Value "up"})
        psa5 (data-umm-cmn/additional-attribute {:Name "novalue" :DataType "STRING" :Description "description"})
        p1 (data-umm-cmn/platform
            {:ShortName "platform_SnB"
             :LongName "platform_Ln B"
             :Instruments
             [(data-umm-cmn/instrument {:ShortName "isnA" :LongName "ilnA" :Technique "itechniqueA"
                                        :ComposedOf [(data-umm-cmn/instrument {:ShortName "ssnA" :LongName "slnA"})
                                                     (data-umm-cmn/instrument {:ShortName "ssnD" :LongName "slnD"
                                                                               :Technique "techniqueD"})]})]})
        p2 (data-umm-cmn/platform
            {:ShortName "platform_SnA spoonA"
             :LongName "platform_LnA"
             :Characteristics
               [(data-umm-cmn/characteristic {:Name "char1" :Description "char1desc" :Value "pv1"})
                (data-umm-cmn/characteristic {:Name "char2" :Description "char2desc" :Value "pv2"})]
             :Instruments
               [(data-umm-cmn/instrument
                {:ShortName "isnB" :LongName "ilnB" :Technique "itechniqueB"
                 :Characteristics
                   [(data-umm-cmn/characteristic {:Name "ichar1" :Description "ichar1desc" :Value "iv1"})
                    (data-umm-cmn/characteristic {:Name "ichar2" :Description "ichar2desc" :Value "iv2"})]
                 :ComposedOf [(data-umm-cmn/instrument
                                {:ShortName "ssnB" :LongName "slnB"
                                 :Characteristics
                                   [(data-umm-cmn/characteristic {:Name "sc1" :Description "sd1" :Value "sv1"})
                                    (data-umm-cmn/characteristic {:Name "sc2" :Description "sd2" :Value "sv2"})]
                                 :Technique "techniqueB"})
                               (data-umm-cmn/instrument {:ShortName "ssnC" :LongName "slnC"
                                                       :Technique "techniqueC"})]})]})
        p3 (data-umm-cmn/platform {:ShortName "spoonA"})
        p4 (data-umm-cmn/platform {:ShortName "SMAP"
                                 :Instruments [(data-umm-cmn/instrument {:ShortName "SMAP L-BAND RADIOMETER"})]})
        p5 (data-umm-cmn/platform {:ShortName "fo&nA"})
        p6 (data-umm-cmn/platform {:ShortName "spo~nA"})
        p7 (data-umm-cmn/platform {:ShortName "platform7"
                                 :Instruments [(data-umm-cmn/instrument {:ShortName "INST7"})]})
        pboost (data-umm-cmn/platform {:ShortName "boost"})
        pr1 (data-umm-cmn/projects "project-short-name")
        pr2 (data-umm-cmn/projects "Proj-2")
        sk1 (data-umm-cmn/science-keyword {:Category "Cat1"
                                 :Topic "Topic1"
                                 :Term "Term1"
                                 :VariableLevel1 "Level1-1"
                                 :VariableLevel2 "Level1-2"
                                 :VariableLevel3 "Level1-3"
                                 :DetailedVariable "SUPER DETAILED!"})
        sk2 (data-umm-cmn/science-keyword {:Category "Hurricane"
                                 :Topic "Laser spoonA"
                                 :Term "Extreme"
                                 :VariableLevel1 "Level2-1"
                                 :VariableLevel2 "Level2-2"
                                 :VariableLevel3 "Level2-3"})
        sk3 (data-umm-cmn/science-keyword {:Category "Cat2"
                                 :Topic "Topic1"
                                 :Term "Term1"
                                 :VariableLevel1 "Level3-1"
                                 :VariableLevel2 "Level3-2"
                                 :VariableLevel3 "Level3-3"
                                 :DetailedVariable "S@PER"})
        skboost (data-umm-cmn/science-keyword {:Category "boost"
                                     :Topic "boost"
                                     :Term "boost"
                                     :VariableLevel1 "boost"
                                     :VariableLevel2 "boost"
                                     :VariableLevel3 "boost"
                                     :DetailedVariable "boost"})
        personnel1 (data-umm-cmn/contact-person "Bob" "Hope" "bob.hope@nasa.gov" "TECHNICAL CONTACT")
        personnel2 (data-umm-cmn/contact-person "Victor" "Fries" "victor.fries@nsidc.gov" "TECHNICAL CONTACT")
        personnel3 (data-umm-cmn/contact-person "Otto" "Octavious" "otto.octavious@noaa.gov")
        tdcs1 (data-umm-c/tiling-identification-system "MISR")
        tdcs2 (data-umm-c/tiling-identification-system "WRS-2")
        org (data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                     :ShortName "Some&Place"})
        url1 (data-umm-cmn/related-url {:URL "http://cmr.earthdata.nasa.gov"
                                        :Type "PROJECT HOME PAGE"
                                        :Description "Earthdata"})
        url2 (data-umm-cmn/related-url {:URL "http://nsidc.org/"
                                        :Type "PROJECT HOME PAGE"
                                        :Description "Home page of National Snow and Ice Data Center"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "coll1" :ShortName "S1"
                                                                            :VersionDescription "VersionDescription"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Mitch made a (merry-go-round)"
                                                                            :ShortName "ABC!XYZ" :Version "V001"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "coll3" :ShortName "S3" :CollectionDataType "OTHER"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll4" :ShortName "S4" :CollectionDataType "OTHER"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll5" :ShortName "Space!Laser"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll6"
                                                                            :ShortName "S6"
                                                                            :DataCenters [org]
                                                                            :Projects pr2
                                                                            :Platforms [p7]}))
        coll7 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll7" :ShortName "S7" :Version "Laser"}))
        coll8 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll8" :ShortName "S8" :ProcessingLevel {:Id "PDQ123"}}))

        coll9 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll09" :ShortName "S9" :ScienceKeywords [sk1 sk2]}))


        coll10 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll10"
                                                                             :ShortName "S10"
                                                                             :SpatialKeywords ["in out"]
                                                                             :ScienceKeywords [sk3]}))
        coll11 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll11" :ShortName "S11" :Platforms [p2 p3 p5 p6]
                                                                             :AdditionalAttributes [psa5]}))
        coll12 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll12" :ShortName "S12"
                                                                             :AdditionalAttributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll13" :ShortName "S13"
                                                                             :TilingIdentificationSystems [tdcs1 tdcs2]}))
        coll14 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll14" :ShortName "spoonA laser"}))
        coll15 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll15" :ShortName "S15" :ProcessingLevel {:Id  "plid1"}
                                                                             :CollectionDataType "SCIENCE_QUALITY" :Platforms [p1]
                                                                             :Abstract "summary" :TemporalKeywords ["tk1" "tk2"]}))
        coll16 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll16" :ShortName "entryid4"}) {:format :dif})
        ;;coll17 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:associated-difs ["DIF-1" "DIF-2"]}))
        coll18 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll18" :ShortName "SNFoobar"}))
        coll20 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Projects pr1 :EntryTitle "Mixed" :ShortName "S20"}))
        coll21 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll21" :ShortName "Laser"}))
        coll22 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll22" :CollectionDataType "NEAR_REAL_TIME"
                                                                             :ShortName "Mixed"}))
        coll23 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "coll23" :ShortName "\"Quoted\" collection"}))
        coll24 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll24" :ShortName "coll24" :Platforms [p4]}))
        ;; Adding personnel here to test keyword search using DataCenter contacts
        coll25 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll25" :ShortName "S25"
                                                                             :RelatedUrls [url1 url2] :ContactPersons [personnel3]}))
        coll-boost (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "boost"
                                                                                 :ShortName "boost"
                                                                                 :Platforms [pboost]
                                                                                 :ScienceKeywords [skboost]}))

        coll26 (d/ingest-umm-spec-collection "PROV4" (data-umm-c/collection {:EntryTitle "coll26" :ShortName "S26"
                                                                             :ContactPersons [personnel1]}) {:format :dif10})
        coll26-1 (d/ingest-umm-spec-collection "PROV4" (data-umm-c/collection {:EntryTitle "coll26 one" :ShortName "S26 (sname one) \"sname one\""
                                                                               :ContactPersons [personnel1]}) {:format :dif10})
        coll27 (d/ingest-umm-spec-collection "PROV5" (data-umm-c/collection {:EntryTitle "coll27" :ShortName "S27" :ContactPersons [personnel2]}) {:format :dif10})]


    (index/wait-until-indexed)

    (testing "search by keyword-phrase unsupported cases."
      (are3 [keyword-str]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})
              json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})]
          (is (= {:errors [(str "keyword phrase mixed with keyword, or another keyword-phrase are not supported. "
                                "keyword phrase has to be enclosed by two escaped double quotes.")]
                  :status 400}
                 parameter-refs
                 json-refs)))

        "mix of keyword and keyword phrase search: not supported yet."
        "Mitch \"a (merry-go-round)\""
        "multiple keyword phrase search: not supported yet."
        "\"Mitch made\" \"a (merry-go-round)\""
        "Missing one \" case"
        "\"a (merry-go-round)"))

    (testing "search by keywords."
      (are [keyword-str items]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})
              json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})
              parameter-matches? (d/refs-match? items parameter-refs)
              json-matches? (d/refs-match? items json-refs)]
          (when-not parameter-matches?
            (println "Parameter search failed")
            (println "Expected:" (map :EntryTitle items))
            (println "Actual:" (map :name (:refs parameter-refs))))
          (when-not json-matches?
            (println "JSON Query search failed")
            (println "Expected:" (map :EntryTitle items))
            (println "Actual:" (map :name (:refs json-refs))))
          (and parameter-matches? json-matches?))

        ;; search by contact persons
        "Bob Hope" [coll26 coll26-1]
        "\"Bob Hope\"" []
        "bob.hope@nasa.gov" [coll26 coll26-1]
        "\"bob.hope@nasa.gov\"" [coll26 coll26-1]
        "Victor" [coll27]
        "victor.fries@nsidc.gov" [coll27]

        ;; search by data center contact
        "Octavious" [coll25]

        "ABC" [coll2]
        "place" [coll6]
        "Laser" [coll21 coll5 coll7 coll9 coll14]
        "ABC V001" [coll2]
        "BLAH" []
        "Space!Laser" [coll5]

        ;; Checking specific fields

        ;; provider
        "PROV1" [coll1 coll2 coll3 coll23]

        ;; entry title
        "coll1" [coll1]
        "\"coll1\"" [coll1]
        "coll26" [coll26 coll26-1]
         "\"coll26\"" [coll26 coll26-1]
        "Mitch made a (merry-go-round)" [coll2]
        "\"Mitch made a (merry-go-round)\"" [coll2]
        "(merry-go-round)" [coll2]
        "\"(merry-go-round)\"" [coll2]
        "merry-go-round" [coll2]
        "\"merry-go-round\"" [coll2]
        "merry" [coll2]
        "\"merry\"" [coll2]
        "merry go round" [coll2]
        "\"merry go round\"" []
        "merry-go" []
        "\"merry-go\"" []

        ;; mix of keyword and keyword phrase search: not supported yet.
        "Mitch \"a (merry-go-round)\"" []
        ;; multiple keyword phrase search: not supported yet.
        "\"Mitch made\" \"a (merry-go-round)\"" []

        ;; entry id
        "ABC!XYZ_V001" [coll2]

        ;; short name
        "XYZ" [coll2]
        "\"XYZ\"" [coll2]
        "sname one" [coll26-1]
        "\"sname one\"" [coll26-1]
        "(sname one)" [coll26-1]
        "\"(sname one)\"" [coll26-1]
        "\"(sname one) \\\"sname one\\\"\"" [coll26-1]

        ;; version id
        "V001" [coll2]

        ;; version description
        "VersionDescription" [coll1]

        ;; processing level id
        "plid1" [coll15]

        ;; collection data type
        "SCIENCE_QUALITY" [coll15]

        ;; collection data type aliases for NEAR_REAL_TIME
        "NEAR_REAL_TIME" [coll22]
        "NRT" [coll22]
        "near_real_time" [coll22]
        "nrt" [coll22]
        "near-real-time" [coll22]
        "near real-time" [coll22]
        "near-real time" [coll22]

        ;; summary
        "summary" [coll15]

        ;; temporal keywords
        "tk1" [coll15]

        ;; spatial keywords - no longer working. see comment in CMR-3895
        ;;;; "in" [coll10]

        ;; two d coord
        "xyz" [coll2]

        ;; archive center
        "some" [coll6]

        ;; attributes
        ;; - name
        "charlie" [coll12]
        ;; - description
        "Generated" [coll12]
        ;; description with no value - see CMR-1129
        "description" [coll11]

        ;; Platforms
        ;; - short name
        "platform_SnA" [coll11]
        ;; - long name (from metadata - not from KMS)
        "platform_ln" [coll15]
        ;; - long name (from KMS - not from the metadata)
        "Soil Moisture Active and Passive Observatory" [coll24]
        ;; - characteristic name
        "char1" [coll11]
        "char2" [coll11]
        "char1 char2" [coll11]
        ;; - characteristic description
        "char1desc" [coll11]
        "char2desc" [coll11]
        "char1desc char2desc" [coll11]
        ;; - characteristic value
        "pv1" [coll11]
        "pv2" [coll11]
        "pv1 pv2" [coll11]



        ;; Instruments
        ;; - short name
        "isnA" [coll15]
        ;; - long name (from metadata - not from KMS)
        "ilnB" [coll11]
        ;; - long name (from KMS - not from metadata)
        "SMAP L-Band Radiometer" [coll24]
        ;; - technique
        "itechniqueB" [coll11]
        "itechniqueA" [coll15]
        ;; - characteristics name
        "ichar1" [coll11]
        "ichar2" [coll11]
        "ichar1 ichar2" [coll11]
        ;; - characteristics desc
        "ichar1desc" [coll11]
        "ichar2desc" [coll11]
        "ichar1desc char2desc" [coll11]
        ;; characteristics value
        "iv1" [coll11]
        "iv2" [coll11]
        "iv1 iv2" [coll11]

        ;; Sensors
        ;; - short name
        "ssnA" [coll15]
        ;; - long name
        "slnB" [coll11]
        ;; - technique
        "techniqueB" [coll11]
        "techniqueD" [coll15]
        "techniqueB techniqueC" [coll11]
        ;; - characteristics name
        "sc1" [coll11]
        "sc2" [coll11]
        "sc1 sc2" [coll11]
        ;; - characteristics desc
        "sd1" [coll11]
        "sd2" [coll11]
        "sd1 sd2" [coll11]
        ;; - characteristics value
        "sv1" [coll11]
        "sv2" [coll11]
        "sv1 sv2" [coll11]

        ;; Science keywords
        ;; - category
        "Cat1" [coll9]
        ;; - topic
        "Topic1" [coll9 coll10]
        ;; - term
        "Term1" [coll9 coll10]
        ;; - variable-levels
        "Level2-1" [coll9]
        "Level2-2" [coll9]
        "Level2-3" [coll9]
        ;; - detailed-variable
        "SUPER" [coll9]

        ;; Related URLs
        "earthdata" [coll25]
        "nsidc" [coll25 coll27]

        ;; Special characters are escaped before sending to Elastic
        "ABC~ZYX" []
        "ABC~" []
        "spo~nA" [coll11]
        "fo&nA" [coll11]
        "A.+&.+C" []
        "S@PER" [coll10]

        ;; search by keywords using wildcard *
        "XY*" [coll2]
        "*aser" [coll21 coll5 coll7 coll9 coll14]
        "p*ce" [coll6]
        "NEA*REA*IME" [coll22]
        "nea*rea*ime" [coll22]
        "\\\"Quoted*" [coll23]

        ;; search by keywords using wildcard ?
        "XY?" [coll2]
        "?aser" [coll21 coll5 coll7 coll9 coll14]
        "p*ace" [coll6]
        "NEAR?REAL?TIME" [coll22]
        "near?real?time" [coll22]))

     (testing "Default boosts on fields"
      (are3 [params scores] (is (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection params)))))

        "short-name"
        {:keyword "SNFoobar"} [short-name-boost]

        "entry-id"
        {:keyword "ABC!XYZ_V001"} [entry-id-boost]

        "project short-name as keyword"
        {:keyword (:ShortName (first pr1))} [project-boost]
        "project short-name as parameter"
        {:project (:ShortName (first pr1))} [project-boost]
        "project long-name"
        {:keyword (:LongName (first pr1))} [project-boost]

        "platform short-name as keyword"
        {:keyword (:ShortName p1)} [platform-boost]
        "platform short-name as parameter"
        {:platform (:ShortName p1)} [platform-boost]
        "platform short-name as parameter with pattern"
        {:platform "*spoonA" "options[platform][pattern]" true} [platform-boost]
        "platform long-name (from metadata)"
        {:keyword (:LongName p1)} [platform-boost]
        "platform long-name (from KMS)"
        {:keyword "Soil Moisture Active and Passive Observatory"} [platform-boost]

        "instrument short-name as keyword"
        {:keyword (:ShortName (first (:Instruments p1)))} [instrument-boost]
        "instrument short-name as parameter"
        {:instrument (:ShortName (first (:Instruments p1)))} [instrument-boost]
        "instrument long-name (from metadata)"
        {:keyword (:LongName (first (:Instruments p1)))} [instrument-boost]
        "instrument long-name (from KMS)"
        {:keyword "L-Band Radiometer"} [instrument-boost]

        ;; In UMM-C sensors are now instruments. All instruments (instruments + child instruments,
        ;; aka sensors) are indexed as both instruments and sensors until sensors search goes
        ;; away. Since they are instruments, there is no longer a sensor boost, but instrument
        ;; boost will be used.
        "sensor short-name as keyword"
        {:keyword (:ShortName (first (:ComposedOf (first (:Instruments p1)))))} [instrument-boost]
        "sensor short-name as parameter"
        {:sensor (:ShortName (first (:ComposedOf (first (:Instruments p1)))))} [instrument-boost]
        "sensor long-name"
        {:keyword (:LongName (first (:ComposedOf (first (:Instruments p1)))))} [instrument-boost]

        "temporal-keywords"
        {:keyword "tk1"} [(k2e/get-boost nil :temporal-keyword)]

        ;;"spatial-keyword" - no longer working. see comment in CMR-3895
        ;;{:keyword "in out"} [(k2e/get-boost nil :spatial-keyword)]

       "science-keywords as keyword"
        {:keyword (:Category sk1)} [science-keywords-boost]
        "science-keywords category as parameter"
        {:science-keywords {:0 {:Category (:Category sk1)}}} [science-keywords-boost]

        "science-keywords topic as parameter"
        {:science-keywords {:0 {:Topic (:Topic sk1)}}} [science-keywords-boost science-keywords-boost]
        "science-keywords term as parameter"
        {:science-keywords {:0 {:Term (:Term sk1)}}} [science-keywords-boost science-keywords-boost]
        "science-keywords variable-level-1 as parameter"
        {:science-keywords {:0 {:VariableLevel1 (:VariableLevel1 sk1)}}} [science-keywords-boost]
        "science-keywords variable-level-2 as parameter"
        {:science-keywords {:0 {:VariableLevel2 (:VariableLevel2 sk1)}}} [science-keywords-boost]
        "science-keywords variable-level-3 as parameter"
        {:science-keywords {:0 {:VariableLevel3 (:VariableLevel3 sk1)}}} [science-keywords-boost]
        "science-keywords any as parameter"
        {:science-keywords {:0 {:any (:Category sk1)}}} [science-keywords-boost]

        "2d coordinate system as keyword"
        {:keyword (:TilingIdentificationSystemName tdcs2)} [two-d-boost]
        "2d coordinate system as parameter"
        {:two-d-coordinate-system-name (:TilingIdentificationSystemName tdcs2)} [two-d-boost]

        "processing level id as keyword"
        {:keyword "PDQ123"} [processing-level-boost]
        "processing level id as parameter"
        {:processing-level-id "PDQ123"} [processing-level-boost]

        "data center as keyword"
        {:keyword (:ShortName org)} [data-center-boost]
        "data center as parameter"
        {:data-center (:ShortName org)} [data-center-boost]
        "archive center as parameter"
        {:archive-center (:ShortName org)} [data-center-boost]

        "version-id"
        {:keyword "V001"} [(k2e/get-boost nil :version-id)]

        "entry-title"
        {:keyword "coll5"} [(k2e/get-boost nil :entry-title)]

        "provider-id"
        {:keyword "PROV1"} [provider-boost provider-boost provider-boost provider-boost]))

    (testing "Combine keyword and param boosts"
     ;; Check that the scores are very close to account for slight difference in
     ;; number coming from elastic.
     (are3 [params score]
        (is (< (Math/abs
                (double (- (/ score 2.0)
                           (:score (first (:refs (search/find-refs :collection params)))))))
               0.0000001))


       "combine keyword, data center, and instrument"
       {:keyword "Proj-2" :data-center "Some&Place" :instrument "INST7"}
       (* project-boost data-center-boost instrument-boost)

       "instrument search and instrument keyword only results in one instrument boost"
       {:keyword "INST7" :instrument "INST7"} instrument-boost)

    (testing "Specified boosts on fields"
      ;; Format to 5 decimal places to account for very slight differences
      (are3 [params scores] (is (= (map #(format "%.5f" (/ % 2.0)) scores)
                                   (map #(format "%.5f" (:score %))
                                        (:refs (search/find-refs :collection params)))))
        "short-name"
        {:keyword "SNFoobar" :boosts {:short-name 2.0}} [2.0]

        "entry-id"
        {:keyword "ABC!XYZ_V001" :boosts {:entry-id 3.1}} [3.1]

        "project short-name"
        {:keyword (:ShortName (first pr1)) :boosts {:project 3.0}} [3.0]

        "platform short-name"
        {:keyword (:ShortName p1) :boosts {:platform 4.0}} [4.0]

        "instrument short-name"
        {:keyword (:ShortName (first (:Instruments p1))) :boosts {:instrument 5.0}} [5.0]

        ;; In UMM-C sensors are now instruments. All instruments (instruments + child instruments,
        ;; aka sensors) are indexed as both instruments and sensors until sensors search goes
        ;; away. Since they are instruments, there is no longer a sensor boost, but instrument
        ;; boost will be used.
        "sensor short-name"
        {:keyword (:ShortName (first (:ComposedOf (first (:Instruments p1))))) :boosts {:instrument 6.0}} [6.0]

        "temporal-keywords"
        {:keyword "tk1" :boosts {:temporal-keyword 7.0}} [7.0]

        ;;"spatial-keyword" - no longer working. see comment in CMR-3895
        ;;{:keyword "in out" :boosts {:spatial-keyword 8.0}} [8.0]

        "science-keywords"
        {:keyword (:Category sk1) :boosts {:science-keywords 9.0}} [9.0]

        "version-id"
        {:keyword "V001" :boosts {:version-id 10.0}} [10.0]

        "provider-id"
        {:keyword "PROV1" :boosts {:provider 10.0}} [10.0 10.0 10.0 10.0]

        "entry-title"
        {:keyword "coll5" :boosts {:entry-title 10.0}} [10.0]

        "mixed boosts"
        {:keyword "Mixed" :boosts {:short-name 10.0 :entry-title 11.0}} [11.0 10.0]

        "no defaults"
        {:keyword (:Category sk1) :boosts {:include-defaults false}} [1.0]

        "matches all fields, do not include defaults"
        {:keyword "boost" :boosts {:short-name 5.0 :include-defaults false}}
        [5.0]

        "matches all fields, use defaults, but override short-name boost"
        {:keyword "boost" :boosts {:short-name 5.0 :include-defaults true}}
        [(* 5.0 entry-title-boost platform-boost science-keywords-boost)]))

    (testing "Setting boosts without keyword search is an error"
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :short_name "SNFoobar"
                                                :boosts {:short-name 2.0}})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Relevance boosting is only supported for keyword queries" (first errors)))))

    (testing "Boosting on invalid field"
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword "Laser"
                                                :boosts {:foo 2.0}})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Cannot set relevance boost on field [foo]." (first errors)))))

    (testing "Boosting with non-numeric values is an error."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword "Laser"
                                                :boosts {:short-name "foo"}})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Relevance boost value [foo] for field [short_name] is not a number." (first errors)))))

    (testing "keyword number of keywords with wildcards exceeds the limit for the given max keyword string length."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword "000000000000000000000* 1* 2* 3* 4* 5* 6* 7* 8* 9* 10* 11* 12* 13* 14* 15* 16* 17* 18* 19* 20* 21* 22* 23* 24? 25* 26?"
                                                :boosts {:short-name 2.0}})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "The CMR permits a maximum of 22 keywords with wildcards in a search, given the max length of the keyword being 22. Your query contains 27 keywords with wildcards" (first errors)))))

    (testing "keyword with too many wildcard is an error."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword "0* 1* 2* 3* 4* 5* 6* 7* 8* 9* 10* 11* 12* 13* 14* 15* 16* 17* 18* 19* 20* 21* 22* 23* 24* 25* 26* 27* 28* 29* 30?"
                                                :boosts {:short-name 2.0}})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Max number of keywords with wildcard allowed is 30" (first errors)))))

    (testing "sorted search"
      (are [params items]
        (let [refs (search/find-refs :collection params)
              matches? (d/refs-match-order? items refs)]
          (when-not matches?
            (println "Expected:" (map :EntryTitle items))
            (println "Actual:" (map :name (:refs refs))))
          matches?)
        {:keyword "Laser spoonA"} [coll9 coll14]
        {:keyword "La?er spoonA"} [coll9 coll14]
        {:keyword "L*er spo*A"} [coll9 coll14]
        {:keyword "L?s* s?o*A"} [coll9 coll14]))

    (testing "sorted search by keywords JSON query."
      (are [keyword-str items]
        (let [refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})
              matches? (d/refs-match-order? items refs)]
          (when-not matches?
            (println "Expected:" (map :EntryTitle items))
            (println "Actual:" (map :name (:refs refs))))
          matches?)
        "Laser spoonA" [coll9 coll14]))

   (testing "sorted search by keywords with sort keys."
      (are [keyword-str sort-key items]
        (let [refs (search/find-refs :collection {:keyword keyword-str :sort-key sort-key})
              matches? (d/refs-match-order? items refs)]
          (when-not matches?
            (println "Expected:" (map :EntryTitle items))
            (println "Actual:" (map :name (:refs refs))))
          matches?)
        "laser" "-entry-title" [coll7 coll5 coll21 coll14 coll9]
        "laser" "score" [coll21 coll7 coll9 coll5 coll14]
        "laser" "+score" [coll7 coll9 coll5 coll14 coll21]
        "laser" "-score" [coll21 coll7 coll9 coll5 coll14]))

    (testing "parameter search by keywords returns score"
      (let [refs (search/find-refs :collection {:keyword "Laser"})]
        (is (every? :score (:refs refs)))))

    (testing "JSON keywords search returns score"
      (let [refs (search/find-refs-with-json-query :collection {} {:keyword "Laser"})]
        (is (every? :score (:refs refs)))))

    (testing "search by multiple keywords returns an error."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :keyword ["Laser" "spoon"]})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Parameter [keyword] must have a single value." (first errors)))))

    (testing "JSON negated keyword search does not return score"
      (let [refs (search/find-refs-with-json-query :collection {} {:not {:keyword "Laser"}})]
        (is (not-any? :score (:refs refs))))))))

;; This test is separated out from the rest of the keyword search tests because we need to
;; ingest this UMM-SPEC collection but it contains some keyword values other tests are using.
;; This would break other tests when doing the keyword searches.
(deftest search-by-more-keywords
  (let [coll1 (d/ingest "PROV1"
                        (-> exp-conv/curr-ingest-ver-example-collection-record
                            (assoc :AncillaryKeywords ["CMR2652AKW1" "CMR2652AKW2"])
                            (assoc :DOI (cm/map->DoiType
                                         {:DOI "doi" :Authority "auth"}))
                            (assoc :DirectoryNames
                                   [(um/map->DirectoryNameType
                                     {:ShortName "CMR2654DNSN1" :LongName "CMR2654DNLN1"})])
                            (assoc :ISOTopicCategories ["environment" "health"])
                            (assoc :ShortName "CMR2652SN1")
                            (assoc :EntryTitle "CMR2652ET1")
                            (assoc :LocationKeywords
                                   [(um/map->LocationKeywordType
                                     {:Category "CONTINENT"
                                      :Type "ASIA"
                                      :Subregion1 "WESTERN ASIA"
                                      :Subregion2 "MIDDLE EAST"
                                      :Subregion3 "GAZA STRIP"
                                      :DetailedLocation "Testing Detailed Location"})]))
                        {:format :umm-json
                         :accept-format :json})
        coll2 (d/ingest "PROV1"
                        (-> exp-conv/curr-ingest-ver-example-collection-record
                            (assoc :AncillaryKeywords ["CMR2652AKW3" "CMR2652AKW4"])
                            (assoc :DirectoryNames
                                   [(um/map->DirectoryNameType
                                     {:ShortName "CMR2654DNSN2" :LongName "CMR2654DNLN2"})])
                            (assoc :ISOTopicCategories ["biota"])
                            (assoc :ShortName "CMR2652SN2")
                            (assoc :EntryTitle "CMR2652ET2"))
                        {:format :umm-json
                         :accept-format :json})
        coll3 (d/ingest-concept-with-metadata-file "data/iso_mends/no_spatial_iso_collection.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :format-key :iso19115})

        coll4 (d/ingest-concept-with-metadata-file "iso-samples/cmr-4192-iso-collection.xml"
                                                   {:provider-id "PROV2"
                                                    :concept-type :collection
                                                    :format-key :iso19115})]

    (index/wait-until-indexed)
    (testing "parameter searches"
      (are3 [keyword-str items]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})]
          (d/assert-refs-match items parameter-refs))
        "Testing parameter search by location keyword"
        "Tuolumne River Basin"
        [coll4]

        "Testing parameter search by location keyword"
        "America"
        [coll4]

        "testing parameter search by shortname keyword in collection whose xml file contains no SpatialExtent content"
        "NSIDC-0705"
        [coll3]

        "testing parameter search by existing ancillary keywords"
        "CMR2652AKW1"
        [coll1]

        "testing parameter search by existing DOI value"
        "dOI"
        [coll1]

        "testing parameter search by existing DirectoryNames keywords"
        "CMR2654DNSN1"
        [coll1]

        "testing parameter search by existing ancillary keywords"
        "CMR2652AKW4"
        [coll2]

        "testing parameter search by existing DirectoryNames keywords"
        "CMR2654DNLN2"
        [coll2]

        "testing parmaeter search by non-existing keywords"
        "CMR2652NOAKW"
        []

        "testing iso-topic-category search - biota"
        "biota"
        [coll2]

        "testing iso-topic-category search - environment"
        "environment"
        [coll1]

        "testing iso-topic-category search - health"
        "health"
        [coll1]

        "testing detailed-location search - Testing Detailed Location"
        "Testing Detailed Location"
        [coll1]))

    (testing "Search collections by location keywords using JSON Query."
      (are3 [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))
        "Testing detailed-location search"
        [coll1] {:location_keyword {:category "CONTINENT"
                                    :type "ASIA"
                                    :subregion_1 "WESTERN ASIA"
                                    :subregion_2 "MIDDLE EAST"
                                    :subregion_3 "GAZA STRIP"
                                    :detailed_location "Testing Detailed Location"}}))

    (testing "json query searchs"
      (are3 [keyword-str items]
        (let [json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})]
          (d/assert-refs-match items json-refs))
        "testing json query search by shortname keyword in the collection whoes xml file contains no SpatialExtent content"
        "NSIDC-0705"
        [coll3]

        "testing json query search by existing ancillary keywords"
        "CMR2652AKW2"
        [coll1]

        "testing json query search by existing DirectoryNames keywords"
        "CMR2654DNLN1"
        [coll1]

        "testing json query search by existing ancillary keywords"
        "CMR2652AKW3"
        [coll2]

        "testing json query search by existing DirectoryNames keywords"
        "CMR2654DNSN2"
        [coll2]

        "testing json query search by non-existing keywords"
        "CMR2652NOAKW"
        []))))

;; This tests that when searching by relevancy that if the score is the same short name ascending is used for
;; sorting the results and then if short name is the same version is used for sorting the results
(deftest search-by-keywords-relevancy-sorting-includes-short-name-and-version
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Terra Aerosol 5-Min L2 Swath 10km V5.1",
                                  :ShortName "MOD04_L2",
                                  :Version "5.1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Aqua Aerosol 5-Min L2 Swath 10km V5.1",
                                  :ShortName "MYD04_L2",
                                  :Version "5.1"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Aqua Aerosol 5-Min L2 Swath 10km V006",
                                  :ShortName "MYD04_L2",
                                  :Version "6"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Terra Aerosol 5-Min L2 Swath 10km V006",
                                  :ShortName "MOD04_L2",
                                  :Version "6"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Aqua Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V5.1",
                                  :ShortName "MYD05_L2",
                                  :Version "5.1"}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Terra Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V5.1",
                                  :ShortName "MOD05_L2",
                                  :Version "5.1"}))
        coll7 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Aqua Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V006",
                                  :ShortName "MYD05_L2",
                                  :Version "6"}))
        coll8 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS/Terra Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V006",
                                  :ShortName "MOD05_L2",
                                  :Version "6"}))
        coll9 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                 {:EntryTitle "MODIS Aerosol Other",
                                  :Projects [(data-umm-cmn/project "MODIS" "ignored")]
                                  :Platforms [(data-umm-cmn/platform {:ShortName "MODIS"})]
                                  :ShortName "Other",
                                  :Version "1"}))]
    (index/wait-until-indexed)
    (let [refs (search/find-refs :collection {:keyword "modis aerosol"})
          expected-order [coll9 ;; higher score
                          ;; The scores of the rest of them are identical
                          coll4 ;; MOD04_L2 6
                          coll1 ;; MOD04_L2 5.1
                          coll8 ;; MOD05_L2 6
                          coll6 ;; MOD05_L2 5.1
                          coll3 ;; MYD04_L2 6
                          coll2 ;; MYD04_L2 5.1
                          coll7 ;; MYD05_L2 6
                          coll5] ;; MYD05_L2 5.1
          matched? (d/refs-match-order?
                    expected-order
                    refs)]
      (when-not matched?
        (println "Actual order: " (pr-str (map :id (:refs refs)))))
      (is matched?))))


(deftest search-by-keywords-with-special-chars
  ;; needed for special charatcter tests
  (let [coll-data [["coll00" "dummy && ||"]
                   ["coll01" "begin!end"]
                   ["coll02" "begin@end"]
                   ["coll03" "begin#end"]
                   ["coll04" "begin$end"]
                   ["coll05" "begin%end"]
                   ["coll06" "begin^end"]
                   ["coll07" "begin&end"]
                   ["coll08" "begin(end"]
                   ["coll09" "begin)end"]
                   ["coll10" "begin-end"]
                   ["coll11" "begin=end"]
                   ["coll12" "begin_end"]
                   ["coll13" "begin+end"]
                   ["coll14" "begin{end"]
                   ["coll15" "begin}end"]
                   ["coll16" "begin[end"]
                   ["coll17" "begin]end"]
                   ["coll18" "begin|end"]
                   ["coll19" "begin\\end"]
                   ["coll20" "begin;end"]
                   ["coll21" "begin'end"]
                   ["coll22" "begin.end"]
                   ["coll23" "begin,end"]
                   ["coll24" "begin/end"]
                   ["coll25" "begin:end"]
                   ["coll26" "begin\"end"]
                   ["coll27" "begin<end"]
                   ["coll28" "begin>end"]
                   ["coll29" "begin?end"]
                   ["coll30" "begin`end"]
                   ["coll31" "begin~end"]
                   ["coll32" "modis foobar"]
                   ["coll33" "bleep blop blorp"]
                   ["coll34" "abcdefghijklmnop"]
                   ["coll35" "foo modis bar"]
                   ["coll36" "akdi modis/terra dke"]
                   ["coll37" "akdi modis-terra dke"]
                   ["coll38" "Dataset with foobar"]
                   ["coll39" "foo54"]
                   ["coll40" "foo67"]
                   ["coll41" "moding"]
                   ["coll42" "outmoded"]
                   ["coll43" "outmodising"]
                   ["coll44" "out-modis-ed"]
                   ["coll45" "carbon*oxygen"]
                   ["coll46" "Dataset no withword"]
                   ["coll47" "begin&&end"]
                   ["coll48" "choco 48and taco"]
                   ["coll49" "choco or taco"]
                   ["coll50" "begin*end"]]
        colls (doall (for [[coll summary] coll-data]
                       (d/ingest-umm-spec-collection "PROV3" (data-umm-c/collection (d/unique-num) {:EntryTitle coll :Abstract summary}))))]
    (index/wait-until-indexed)
    (are [keyword-str indexes]
         (let [refs (search/find-refs :collection {:keyword keyword-str :page_size 51})
               items (map (partial nth colls) indexes)
               matches? (d/refs-match? items refs)]
           (when-not matches?
             (println "Expected:" (map :EntryTitle items))
             (println "Actual:" (map :name (:refs refs))))
           matches?)
         "begin!end" [1]
         "\"begin!end\"" [1]
         "begin\\end" [19]
         "\"begin\\end\"" [19]
         "begin\\\"end" [26]
         "\"begin\\\"end\"" [26]
         "begin<end" [27]
         "\"begin<end\"" [27]
         "begin\\?end" [29]
         "\"begin\\?end\"" [29]
         "begin~end" [31]
         "\"begin~end\"" [31]
         "begin\\*end" [50]
         "\"begin\\*end\"" [50]
         "begin" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 47 50]
         "\"begin\"" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 47 50]
         "end" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 47 50]
         "\"end\"" [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 47 50]
         "&&" [0]
         "\"&&\"" [0]
         "||" [0]
         "\"||\"" [0]
         "48AND" [48]
         "\"48AND\"" [48]
         "OR" [49]
         "\"OR\"" [49])))

;; Test that the same collection short-name with different versions comes back
;; in descending order by version, even if the versions are in different formats
;; i.e. 001 vs 2
(deftest version-sort
  (let [coll-v1 (d/ingest-umm-spec-collection
                  "PROV1"
                  (data-umm-c/collection
                           {:EntryTitle "MODIS/Terra Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V001",
                            :ShortName "MOD05_L2",
                            :Version "001"}))
        coll-v2 (d/ingest-umm-spec-collection
                  "PROV1"
                  (data-umm-c/collection
                           {:EntryTitle "MODIS/Terra Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V002",
                            :ShortName "MOD05_L2",
                            :Version "2"}))
        coll-v3 (d/ingest-umm-spec-collection
                  "PROV1"
                  (data-umm-c/collection
                           {:EntryTitle "MODIS/Terra Total Precipitable Water Aerosol 5-Min L2 Swath 1km and 5km V003",
                            :ShortName "MOD05_L2",
                            :Version "003"}))
        _ (index/wait-until-indexed)
        refs (search/find-refs :collection {:keyword "MOD05_L2"})]
     (is (d/refs-match-order? [coll-v3 coll-v2 coll-v1] refs))))
