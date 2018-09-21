(ns cmr.umm-spec.umm-g.granule
  "Contains functions for parsing UMM-G JSON into umm-lib granule model
  and generating UMM-G JSON from umm-lib granule model."
  (:require
   [cheshire.core :as json]
   [cmr.umm.umm-granule :as g]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.common.util :as util])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g->CollectionRef
  "Returns a UMM ref element from a parsed UMM-G JSON"
  [umm-g-json]
  (let [collection-ref (:CollectionReference umm-g-json)]
    (g/map->CollectionRef {:entry-title (:EntryTitle collection-ref)
                           :short-name (:ShortName collection-ref)
                           :version-id (:Version collection-ref)})))

; (defn- umm-g->SpatialCoverage
;   [umm-g-json]
;   (let [geometry (get-in umm-g-json [:SpatialExtent :HorizontalSpatialDomain :Geometry])
;         orbit (get-in umm-g-json [:SpatialExtent :HorizontalSpatialDomain :Orbit])]
;     (when (or geometry orbit)
;       (g/map->SpatialCoverage {:geometries (when geometry (s/geometry-element->geometries geom-elem))
;                                :orbit (when orbit (s/xml-elem->Orbit orbit-elem))}))))

(defn umm-g->Granule
  "Returns a UMM Granule from a parsed UMM-G JSON"
  [umm-g-json]
  (let [coll-ref (umm-g->CollectionRef umm-g-json)]
    (g/map->UmmGranule {:granule-ur (:GranuleUR umm-g-json)
                        ; :data-provider-timestamps data-provider-timestamps
                        :collection-ref coll-ref
                        ; :data-granule (xml-elem->DataGranule umm-g-json)
                        ; :access-value (cx/double-at-path umm-g-json [:RestrictionFlag])
                        ; :temporal (gt/xml-elem->Temporal umm-g-json)
                        ; :orbit-calculated-spatial-domains (ocsd/xml-elem->orbit-calculated-spatial-domains umm-g-json)
                        ; :platform-refs (p-ref/xml-elem->PlatformRefs umm-g-json)
                        ; :project-refs (xml-elem->project-refs umm-g-json)
                        ; :cloud-cover (cx/double-at-path umm-g-json [:CloudCover])
                        ; :two-d-coordinate-system (two-d/xml-elem->TwoDCoordinateSystem umm-g-json)
                        ; :related-urls (ru/xml-elem->related-urls umm-g-json)
                        ; :spatial-coverage (xml-elem->SpatialCoverage umm-g-json)
                        ; :measured-parameters (mp/xml-elem->MeasuredParameters umm-g-json)
                        ; :product-specific-attributes (psa/xml-elem->ProductSpecificAttributeRefs umm-g-json)
                        })))

(defn Granule->umm-g
  "Returns UMM-G JSON from a umm-lib Granule"
  [granule]
  (let [{{:keys [entry-title short-name version-id entry-id]} :collection-ref
         {:keys [insert-time update-time delete-time]} :data-provider-timestamps
         :keys [granule-ur data-granule access-value temporal orbit-calculated-spatial-domains
                platform-refs project-refs cloud-cover related-urls product-specific-attributes
                spatial-coverage orbit two-d-coordinate-system measured-parameters]} granule
        insert-time (when insert-time
                      {:Date (str insert-time)
                       :Type "Insert"})
        update-time (when update-time
                      {:Date (str update-time)
                       :Type "Update"})
        delete-time (when delete-time
                      {:Date (str delete-time)
                       :Type "Delete"})]
    {:GranuleUR granule-ur
     :ProviderDates (vec (remove nil? [insert-time update-time delete-time]))
     :CollectionReference (if (some? entry-title)
                            {:EntryTitle entry-title}
                            {:ShortName short-name
                             :Version version-id})
     }
    ))
