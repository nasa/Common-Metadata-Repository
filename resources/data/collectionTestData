CREATE (NSIDC:Provider {ShortName:'NSIDC', LongName:'National Snow and Ice Data Center'})

CREATE (C1386207787NSIDCV0:Collection {ShortName:'NSIDC-0103', version:2, title:'RAMP AMM-1 SAR Image Mosaic of Antarctica, Version 2', doi:'Not provided'})
CREATE (C1386206814NSIDCV0:Collection {ShortName:'GGD318', version:2, title:'Circum-Arctic Map of Permafrost and Ground-Ice Conditions, Version 2', doi:'Not provided'})
CREATE
  (NSIDC)-[:PROVIDES {type:['collection']}]->(C1386207787NSIDCV0),
  (NSIDC)-[:PROVIDES {type:['collection']}]->(C1386206814NSIDCV0)

CREATE (OGCSWMS:Service {ShortName:'OGCS WMS', LongName:'Open Geospatial Consortium (OGC) Services - WMS', type:'WMS', version:'1'})
CREATE (OGCSWCS:Service {ShortName:'OGCS WCS', LongName:'Open Geospatial Consortium (OGC) Services - WCS', type:'WCS', version:'1'})
CREATE
  (OGCSWMS)-[:SERVICES {type:['collection']}]->(C1386207787NSIDCV0),
  (OGCSWCS)-[:SERVICES {type:['collection']}]->(C1386206814NSIDCV0)

CREATE (Snow:Variable {Name:'Snow', Type:'Science'})
CREATE (SnowQuality1:Variable {Name:'SnowQuality 1', Type:'Quality'})
CREATE (SnowQuality2:Variable {Name:'SnowQuality 2', Type:'Quality'})
CREATE (SnowSet:Variable {Name:'SnowSet'})
CREATE
  (Snow)-[:partOf {type:['set']}]->(SnowSet),
  (SnowQuality1)-[:partOf {type:['set']}]->(SnowSet),
  (SnowQuality2)-[:partOf {type:['set']}]->(SnowSet)

CREATE (RADARSAT1:Platform {ShortName:'RADARSAT-1', LongName:'RADARSAT-1'})
CREATE
  (RADARSAT1)-[:acquistion {type:['collection']}]->(C1386207787NSIDCV0)

CREATE (SAR:Instrument {LongName:'Synthetic Aperture Radar'})
CREATE
  (SAR)-[:instrumentOf {type:['platform']}]->(RADARSAT1)

CREATE (C1386207787NSIDCV0URL1:PublicationURL {urlContentType:"PublicationURL", type:'VIEW RELATED INFORMATION', Subtype:'', Description:'Access these data using the Open Geospatial Consortium (OGC) Services.', URL:'http://nsidc.org/data/atlas/ogc_services.html'})
CREATE (C1386207787NSIDCV0URL2:DistributionURL {urlContentType:"DistributionURL", type:'GET DATA', Subtype:'', Description:'Direct download via HTTPS protocol.', URL:'https://daacdata.apps.nsidc.org/pub/DATASETS/nsidc0103_radarsat_sar/'})
CREATE (C1386207787NSIDCV0URL3:PublicationURL {urlContentType:"PublicationURL", type:'VIEW RELATED INFORMATION', Subtype:'', Description:'Documentation explaining the data and how it was processed.', URL:'http://nsidc.org/data/docs/daac/nsidc0103_ramp_mosaic.gd.html'})
CREATE (C1386207787NSIDCV0URL4:DistributionURL {urlContentType:"DistributionURL", type:'GET SERVICE', Subtype:'WMS', Description:'Access these data using the Open Geospatial Consortium (OGC) Services - this one was added for demo purposes.', URL:'http://nsidc.org/data/atlas/getData/granule/subsetter'})
CREATE
  (C1386207787NSIDCV0URL1)-[:documentsService {type:['url']}]->(C1386207787NSIDCV0),
  (C1386207787NSIDCV0URL2)-[:distributes {type:['url']}]->(C1386207787NSIDCV0),
  (C1386207787NSIDCV0URL3)-[:documents {type:['url']}]->(C1386207787NSIDCV0),
  (C1386207787NSIDCV0URL4)-[:services {type:['url']}]->(C1386207787NSIDCV0),
  (C1386207787NSIDCV0URL4)-[:services {type:['url']}]->(C1386206814NSIDCV0),
  (C1386207787NSIDCV0URL4)-[:describes {type:['access']}]->(OGCSWMS),
  (C1386207787NSIDCV0URL4)-[:describes {type:['access']}]->(OGCSWCS),
  (C1386207787NSIDCV0URL1)-[:documentsService {type:['url']}]->(C1386206814NSIDCV0)

CREATE (C1386206814NSIDCV0URL1:DistributionURL {urlContentType:"DistributionURL", type:'GET DATA', Subtype:'', Description:'Direct download via HTTPS protocol.', URL:'http://nsidc.org/forms/GGD318_or.html?major_version=2'})
CREATE (C1386206814NSIDCV0URL2:PublicationURL {urlContentType:"PublicationURL", type:'VIEW RELATED INFORMATION', Subtype:'', Description:'Documentation explaining the data and how it was processed.', URL:'http://nsidc.org/data/docs/fgdc/ggd318_map_circumarctic/index.html'})

CREATE
  (C1386206814NSIDCV0URL1)-[:distributes {type:['url']}]->(C1386206814NSIDCV0),
  (C1386206814NSIDCV0URL2)-[:documents {type:['url']}]->(C1386206814NSIDCV0)
