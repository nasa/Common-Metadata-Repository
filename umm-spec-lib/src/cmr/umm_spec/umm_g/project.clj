(ns cmr.umm-spec.umm-g.project
  "Contains functions for parsing UMM-G JSON projects into umm-lib granule modelProjectRefs
  and generating UMM-G JSON projects from umm-lib granule model ProjectRefs."
  (:require
   [cmr.umm-spec.umm-g.instrument :as instrument]
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-project->ProjectRef
  "Returns the umm-lib granule model PlatformRef from the given UMM-G Platform."
  [project]
  (let [short-name (:ShortName project)
        campaigns (:Campaigns project)]
    (g/map->ProjectRef
     {:short-name short-name
      :campaigns campaigns})))

(defn umm-g-projects->ProjectRefs
  "Returns the umm-lib granule model ProjectRefs from the given UMM-G Platforms."
  [projects]
  (seq (map umm-g-project->ProjectRef projects)))

(defn ProjectRefs->umm-g-projects
  "Returns the UMM-G Platforms from the given umm-lib granule model ProjectRefs."
  [project-refs]
  (when (seq project-refs)
    (for [project-ref project-refs]
      (let [{:keys [short-name campaigns]} project-ref]
        {:ShortName short-name
         :Campaigns campaigns}))))
