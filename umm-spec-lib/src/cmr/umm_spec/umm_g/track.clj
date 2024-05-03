(ns cmr.umm-spec.umm-g.track
  "Contains functions for parsing UMM-G JSON Track into umm-lib granule model
   Track and generating UMM-G JSON Track from umm-lib granule model Track."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import
   (cmr.umm.umm_granule UmmGranule)))

(defn- umm-g-pass->TrackPass
  "Returns the umm-lib granule model TrackPass from the given UMM-G Pass."
  [track-pass]
  (g/map->TrackPass
     (let [{:keys [Pass Tiles]} track-pass]
       {:pass Pass
        :tiles Tiles})))

(defn umm-g-track->Track
  "Returns the umm-lib granule model Track from the given UMM-G Track."
  [track]
  (when track
    (g/map->Track
     (let [{:keys [Cycle Passes]} track]
       {:cycle Cycle
        :passes (map umm-g-pass->TrackPass Passes)}))))

(defn- TrackPass->umm-g-pass
  "Returns the UMM-G Pass from the given umm-lib granule model TrackPass."
  [track-pass]
  (let [{:keys [pass tiles]} track-pass]
    {:Pass pass
     :Tiles tiles}))

(defn Track->umm-g-track
  "Returns the UMM-G Track from the given umm-lib granule model Track."
  [track]
  (when track
    (let [{:keys [cycle passes]} track]
      {:Cycle cycle
       :Passes (map TrackPass->umm-g-pass passes)})))
