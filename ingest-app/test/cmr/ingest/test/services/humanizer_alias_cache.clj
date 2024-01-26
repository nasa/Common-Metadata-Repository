(ns cmr.ingest.test.services.humanizer-alias-cache
  "This tests some of the more complicated functions of cmr.ingest.services.humanizer_alias_cache"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.services.humanizer-alias :as humanizer-alias]))

(deftest get-field-aliases-test
  (are3 [exp-field-aliases field-aliases-retrieved]
        (is (= exp-field-aliases field-aliases-retrieved))

        "Testing plat alias doesn't exist in the platforms"
        [{:ShortName "am-1" :Otherfields "other-terra-values"}
         {:ShortName "AM-1" :Otherfields "other-terra-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:ShortName "Terra" :Otherfields "other-terra-values"}]
            :ShortName
            {"TERRA" ["AM-1" "am-1"]})

        "Testing plat alias exists in the platforms"
        [{:ShortName "am-1" :Otherfields "other-terra-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:ShortName "Terra" :Otherfields "other-terra-values"}
             {:ShortName "AM-1" :Otherfields "other-am-1-values"}]
            :ShortName
            {"TERRA" ["AM-1" "am-1"]})

        "Testing tile alias doesn't exist in the tiles"
        [{:TilingIdentificationSystemName "tile-2" :Otherfields "other-tile-values"}
         {:TilingIdentificationSystemName "tile-1" :Otherfields "other-tile-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:TilingIdentificationSystemName "Tile" :Otherfields "other-tile-values"}]
            :TilingIdentificationSystemName
            {"TILE" ["tile-1" "tile-2"]})

        "Testing tile alias exists in the tiles"
        [{:TilingIdentificationSystemName "tile-1" :Otherfields "other-tile-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:TilingIdentificationSystemName "Tile" :Otherfields "other-tile-values"}
             {:TilingIdentificationSystemName "tile-2" :Otherfields "other-tile-2-values"}]
            :TilingIdentificationSystemName
            {"TILE" ["tile-1" "tile-2"]})

        "Testing instr alias doesn't exist in the instruments"
        [{:ShortName "instr-1" :Otherfields "other-instr-values"}
         {:ShortName "instr-2" :Otherfields "other-instr-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:ShortName "Instrument" :Otherfields "other-instr-values"}]
            :ShortName
            {"INSTRUMENT" ["instr-1" "instr-2"]})

        "Testing instr alias exists in the instruments"
        [{:ShortName "instr-1" :Otherfields "other-instr-values"}]
        (#'humanizer-alias/get-field-aliases
            [{:ShortName "Instrument" :Otherfields "other-instr-values"}
             {:ShortName "instr-2" :Otherfields "other-instr-2-values"}]
            :ShortName
            {"INSTRUMENT" ["Instrument" "instr-1" "instr-2"]})))
