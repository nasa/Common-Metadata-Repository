(ns cmr.umm.echo10.granule.platform-ref
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]
            [cmr.umm.echo10.granule.instrument-ref :as inst]))

(defn xml-elem->PlatformRef
  [platform-ref-elem]
  (let [short-name (cx/string-at-path platform-ref-elem [:ShortName])
        instrument-refs (inst/xml-elem->InstrumentRefs platform-ref-elem)]
    (g/map->PlatformRef
      {:short-name short-name
       :instrument-refs instrument-refs})))

(defn xml-elem->PlatformRefs
  [granule-element]
  (seq (map xml-elem->PlatformRef
            (cx/elements-at-path
              granule-element
              [:Platforms :Platform]))))

(defn generate-platform-refs
  [platform-refs]
  (when-not (empty? platform-refs)
    (x/element
      :Platforms {}
      (for [platform-ref platform-refs]
        (let [{:keys [short-name instrument-refs]} platform-ref]
          (x/element :Platform {}
                     (x/element :ShortName {} short-name)
                     (inst/generate-instrument-refs instrument-refs)))))))
