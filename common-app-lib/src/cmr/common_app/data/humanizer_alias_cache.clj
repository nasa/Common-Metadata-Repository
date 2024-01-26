(ns cmr.common-app.data.humanizer-alias-cache
		"Defines common functions and defs for humanizer-alias-cache.
		Structure of the hash-cache is as follows:
		<field-name> --> <non-humanized-source-to-aliases-map>

		Example:
		'platform' --> { 'TERRA' --> ['AM-1', 'am-1', 'AM 1']
																	'OTHERPLATFORMS' --> ['otheraliases']}
		'tiling_system_name' --> {'TILE' --> ['tile_1', 'tile_2']
																												'OTHERTILES' --> ['otheraliases']}
		'instrument' --> {'INSTRUMENT' --> ['instr1', 'instr2']
																				'OTHERINSTRUMENTS' --> ['otheraliases']}"
		(:require
				[clojure.string :as str]
				[cmr.common.hash-cache :as hash-cache]
				[cmr.common.log :as log :refer (debug info warn error)]
				[cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
				[cmr.transmit.humanizer :as humanizer]))

(def humanizer-alias-cache-key
		"The cache key to use when storing with caches in the system."
		:humanizer-alias-cache-by-field-name)

(defn create-cache-client
		"Creates an instance of the cache."
		[]
		(redis-hash-cache/create-redis-hash-cache {:keys-to-track [humanizer-alias-cache-key]}))

(defn- humanizer-group-by-field
		"A custom group-by function for use in the create-humanizer-alias-map
		function."
		[humanizer]
		(group-by
				:field
				(map
						#(select-keys % [:field :replacement_value :source_value])
						(filter #(= (:type %) "alias") humanizer))))

(defn- humanizer-group-by-replacement-value
		"A custom group-by function for use in the create-humanizer-alias-map
		function.

		In particular, this function converts the value assciated with
		:replacement_value to upper-case before group-by in order to cover the case
		in test-humanizers.json where there are multiple :replacement_value that only
		differ in upper-lower cases."
		[v1]
		(group-by
				:replacement_value
				(->>  v1
										(map #(select-keys % [:replacement_value :source_value]))
										(map #(update % :replacement_value str/upper-case)))))

(defn- create-humanizer-alias-map
		"Creates a map of humanizer aliases by type from the humanizer map and returns in the format below.
			Note: All the replacement_value are UPPER-CASED, so when using this map to get
			all the non-humanized source values for a given collection's platform,
			tile, or instrument, they need to be UPPER-CASED as well.
			{\"platform\" {\"TERRA\" [\"AM-1\" \"am-1\" \"AM 1\"] \"OTHERPLATFORMS\" [\"otheraliases\"]}
				\"tiling_system_name\" {\"TILE\" [\"tile_1\" \"tile_2\"] \"OTHERTILES\" [\"otheraliases\"]}
				\"instrument\" {\"INSTRUMENT\" [\"instr1\" \"instr2\"] \"OTHERINSTRUMENTS\" [\"otheraliases\"]}}"
		[humanizer]
		(into
				{}
				(for [[k1 v1] (humanizer-group-by-field humanizer)]
						[k1 (into
												{}
												(for [[k2 v2] (humanizer-group-by-replacement-value v1)]
														[k2 (map :source_value v2)]))])))

(defn refresh-entire-cache
		"Refreshes the humanizer alias cache."
		[context]
		(info "Refreshing entire humanizer alias cache")
		(let [_ (println "inside humanizer's refresh-cache")
								humanizer-alias-cache (hash-cache/context->cache context humanizer-alias-cache-key)
								humanizers (humanizer/get-humanizers context) ;; TODO this is coming from the transmit lib... so maybe this func should not be in common, but be in bootstrap
								_ (println "humanizer found in refresh-cache = " (pr-str humanizers))
								humanizers-alias-map (create-humanizer-alias-map humanizers)]
				(doseq [[humanizer-field get-non-humanized-source-to-aliases-map] humanizers-alias-map]
						(hash-cache/set-value humanizer-alias-cache
																												humanizer-alias-cache-key
																												humanizer-field
																												get-non-humanized-source-to-aliases-map))
				(info (str "Humanizer alias cache refresh complete."
															" humanizer-alias-cache size: " (hash-cache/cache-size humanizer-alias-cache humanizer-alias-cache-key) " bytes"))))

(defn get-non-humanized-source-to-aliases-map
		"Returns the non-humanized-source-to-aliases-map.
		Structure:
		non-humanized-source-name-string --> list of aliases strings

		Example:
		Given: humanizer-field-name = 'platform'
		Returns: { 'TERRA' --> ['AM-1', 'am-1', 'AM 1']
													'OTHERPLATFORMS' --> ['otheraliases']}"
		[context humanizer-field-name]
		(let [humanizer-alias-cache (hash-cache/context->cache context humanizer-alias-cache-key)
								_ (println "inside get-humanizer-alias-map")
								non-humanized-source-to-aliases-map (hash-cache/get-value humanizer-alias-cache
																																																																						humanizer-alias-cache-key
																																																																						humanizer-field-name)
								_ (println "humanizer-alias-map found = " (pr-str non-humanized-source-to-aliases-map))]
				non-humanized-source-to-aliases-map))

(defconfig humanizer-alias-cache-job-refresh-rate
											"Number of seconds between refreshes of the humanizer alias cache."
											{:default 3600
												:type Long})
(defjob RefreshHumanizerAliasCache
								[ctx system]
								(refresh-entire-cache {:system system}))

(defn refresh-humanizer-alias-cache-job
		[job-key]
		{:job-type RefreshHumanizerAliasCache
			:job-key job-key
			:interval (humanizer-alias-cache-job-refresh-rate)})
