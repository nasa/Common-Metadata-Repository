(ns cmr.umm-spec.umm-g.platform
  "Contains functions for parsing UMM-G JSON platforms into umm-lib granule model PlatformRefs
  and generating UMM-G JSON platforms from umm-lib granule model PlatformRefs."
  (:require
   [cmr.umm-spec.umm-g.instrument :as instrument]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-platform->PlatformRef
  "Returns the umm-lib granule model PlatformRef from the given UMM-G Platform."
  [platform]
  (let [short-name (:ShortName platform)
        instrument-refs (instrument/umm-g-instruments->InstrumentRefs (:Instruments platform))]
    (g/map->PlatformRef
     {:short-name short-name
      :instrument-refs instrument-refs})))

(defn umm-g-platforms->PlatformRefs
  "Returns the umm-lib granule model PlatformRefs from the given UMM-G Platforms."
  [platforms]
  (seq (map umm-g-platform->PlatformRef platforms)))

(defn PlatformRefs->umm-g-platforms
  "Returns the UMM-G Platforms from the given umm-lib granule model PlatformRefs."
  [platform-refs]
  (when (seq platform-refs)
    (for [platform-ref platform-refs]
      (let [{:keys [short-name instrument-refs]} platform-ref]
        {:ShortName short-name
         :Instruments (instrument/InstrumentRefs->umm-g-instruments instrument-refs)}))))
