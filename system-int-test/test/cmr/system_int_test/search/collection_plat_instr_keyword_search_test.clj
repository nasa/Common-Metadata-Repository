(ns cmr.system-int-test.search.collection-plat-instr-keyword-search-test
  "Integration test for CMR collection Platform/Instrument search by keyword terms"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.search.data.keywords-to-elastic :as k2e]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.models.umm-collection-models :as um]
    [cmr.umm-spec.models.umm-common-models :as cm]
    [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3" "provguid4" "PROV4" "provguid5" "PROV5"}))

(deftest search-by-keywords
  (let [psa1 (data-umm-c/additional-attribute {:Name "novalue" :DataType "STRING" :Description "description"})
        p1 (data-umm-c/platform
            {:ShortName "platform_SnB"
             :LongName "platform_Ln B"
             :Instruments
             [(data-umm-c/instrument {:ShortName "isnA" :LongName "ilnA" :Technique "itechniqueA"
                                      :ComposedOf [(data-umm-c/instrument {:ShortName "ssnA" :LongName "slnA"})
                                                   (data-umm-c/instrument {:ShortName "ssnD" :LongName "slnD"
                                                                           :Technique "techniqueD"})]})]})
        p2 (data-umm-c/platform
            {:ShortName "platform_SnA spoonA"
             :LongName "platform_LnA"
             :Characteristics 
               [(data-umm-c/characteristic {:Name "char1" :Description "char1desc" :Value "pv1"})
                (data-umm-c/characteristic {:Name "char2" :Description "char2desc" :Value "pv2"})]
             :Instruments
               [(data-umm-c/instrument 
                {:ShortName "isnB" :LongName "ilnB" :Technique "itechniqueB"
                 :Characteristics 
                   [(data-umm-c/characteristic {:Name "ichar1" :Description "ichar1desc" :Value "iv1"})
                    (data-umm-c/characteristic {:Name "ichar2" :Description "ichar2desc" :Value "iv2"})]
                 :ComposedOf [(data-umm-c/instrument 
                                {:ShortName "ssnB" :LongName "slnB"
                                 :Characteristics
                                   [(data-umm-c/characteristic {:Name "sc1" :Description "sd1" :Value "sv1"})
                                    (data-umm-c/characteristic {:Name "sc2" :Description "sd2" :Value "sv2"})]
                                 :Technique "techniqueB"})
                               (data-umm-c/instrument {:ShortName "ssnC" :LongName "slnC"
                                                       :Technique "techniqueC"})]})]})
        p3 (data-umm-c/platform {:ShortName "spoonA"})
        p4 (data-umm-c/platform {:ShortName "SMAP"
                                 :Instruments [(data-umm-c/instrument {:ShortName "SMAP L-BAND RADIOMETER"})]})
        p5 (data-umm-c/platform {:ShortName "fo&nA"})
        p6 (data-umm-c/platform {:ShortName "spo~nA"})
        coll1 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll1" :ShortName "S1" :Platforms [p2 p3 p5 p6]
                                                                             :AdditionalAttributes [psa1]}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll2" :ShortName "S2" :ProcessingLevel {:Id  "plid1"}
                                                                             :CollectionDataType "SCIENCE_QUALITY" :Platforms [p1]
                                                                             :Abstract "summary" :TemporalKeywords ["tk1" "tk2"]}))
        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "coll3" :ShortName "coll3" :Platforms [p4]}))]

    (index/wait-until-indexed)

    (testing "search by keywords."
      (are3 [keyword-str items]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})
              json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})]
          (d/assert-refs-match items parameter-refs)
          (d/assert-refs-match items json-refs))
        
        "Platform short name"
        "platform_SnA" [coll1]
        
        "Platform long name" 
        "platform_ln" [coll2]
        
        "Platform long name (from KMS - not from the metadata)"
        "Soil Moisture Active and Passive Observatory" [coll3]
       
        "Platform characteristics name1"  
        "char1" [coll1]

        "Platform characteristics name2"
        "char2" [coll1]

        "Platform characteristics name1 and name2"
        "char1 char2" [coll1]

        "Platform characteristics desc1" 
        "char1desc" [coll1]

        "Platform characteristics desc2"
        "char2desc" [coll1]

        "Platform characteristics desc1 and desc2"
        "char1desc char2desc" [coll1]
       
        "Platform characteristics value1" 
        "pv1" [coll1]

        "Platform characteristics value1"
        "pv2" [coll1]

        "Platform characteristics value1 and value2"
        "pv1 pv2" [coll1] 
         

        
        "Instrument short name"
        "isnA" [coll2]

        "Instrument long name (from metadata - not from KMS)"
        "ilnB" [coll1]

        "Instrument long name (from KMS - not from metadata)"
        "SMAP L-Band Radiometer" [coll3]

        "Instrument techniqueB"
        "itechniqueB" [coll1]

        "Instrument techiqueA"
        "itechniqueA" [coll2]

        "Instrument characteristics name1" 
        "ichar1" [coll1]

        "Instrument characteristics name2"
        "ichar2" [coll1]

        "Instrument characteristics name1 and name2"
        "ichar1 ichar2" [coll1]

        "Instrument characteristics desc1" 
        "ichar1desc" [coll1]

        "Instrument characteristics desc2"
        "ichar2desc" [coll1]

        "Instrument characteristics desc1 and desc2"
        "ichar1desc char2desc" [coll1]
       
        "Instrument characteristics value1" 
        "iv1" [coll1]

        "Instrument characteristics value2"
        "iv2" [coll1]

        "Instrument characteristics value1 and value2"
        "iv1 iv2" [coll1]     

        
        "Child Instrument short name"
        "ssnA" [coll2]
       
        "Child Instrument long name" 
        "slnB" [coll1]
       
        "Child Instrument techniqueB" 
        "techniqueB" [coll1]

        "Child Instrument techniqueD"
        "techniqueD" [coll2]

        "Child Instrument techniqueB and C"
        "techniqueB techniqueC" [coll1]
       
        "Child Instrument characteristics name1" 
        "sc1" [coll1]

        "Child Instrument characteristics name2" 
        "sc2" [coll1]

        "Child Instrument characteristics name1 and name2"
        "sc1 sc2" [coll1]
       
        "Child Instrument characteristics desc1" 
        "sd1" [coll1]

        "Child Instrument characteristics desc2"
        "sd2" [coll1]

        "Child Instrument characteristics desc1 and desc2"
        "sd1 sd2" [coll1]
       
        "Child Instrument characteristics value1" 
        "sv1" [coll1]
       
        "Child Instrument characteristics value2" 
        "sv2" [coll1]

        "Child Instrument characteristics value1 and value2"
        "sv1 sv2" [coll1]))))

