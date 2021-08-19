(ns cmr.umm-spec.umm-g.projects
  "Contains functions for parsing UMM-G JSON projects into umm-lib granule modelProjectRefs
  and generating UMM-G JSON projects from umm-lib granule model ProjectRefs."
  (:require
   [cmr.umm-spec.util :as util]))

(defn umm-g-projects->ProjectRefs
  "Returns the umm-lib granule model ProjectRefs from the given UMM-G Projects. Until
   projects and campaings get sorted out they need to be put together."
  [projects]
  (let [projs (map :ShortName projects)
        campaigns (mapcat :Campaigns projects)]
    (seq (distinct (concat projs campaigns)))))

(defn ProjectRefs->umm-g-projects
  "Returns the UMM-G Projects from the given umm-lib granule model ProjectRefs."
  [project-refs]
  (when (seq project-refs)
    [{:ShortName util/not-provided
      :Campaigns project-refs}]))
