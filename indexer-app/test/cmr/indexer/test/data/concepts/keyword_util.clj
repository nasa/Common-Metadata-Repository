(ns cmr.indexer.test.data.concepts.keyword-util
  "Functions for testing cmr.indexer.data.concepts.keyword-util namespace."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.indexer.data.concepts.collection.keyword :as ckw]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as es]))

(def sample-umm-collection-concept
  "This sample UMM Collection data is a mish-mash of several examples, done this
  way simply to provide full testing coverage in a single record. It is not
  intended to represent an actual collection and should not be used for anything
  other than testing."
  {:Abstract "An abstract summary"
   :AdditionalAttributes [{:Name "ALBEDOFILEID"
                           :Description "ID of the kernel albedo table used."
                           :Value "aa-value-0"
                           :DataType "INT"}
                          {:Name "ASTERMapProjection"
                           :Description "The map projection of the granule"
                           :Value "aa-value-1"
                           :DataType "STRING"}]
   :AncillaryKeywords ["LP DAAC" "EOSDIS" "USGS/EROS" "ESIP" "USGS" "LPDAAC" "(TMPA-RT)" "(USGS_EROS)" "(TMPA-RT-MULTI-TERM)"]
   :AssociatedDOIs [{:DOI "Associated-DOI-1"
                     :Title "Assoc Title 1"
                     :Authority "https://doi.org"}
                    {:DOI "Associated-DOI-2"}]
   :CollectionCitations [{:Creator "Bowen Island Forest and Water Management Society (BIFWMS)"
                          :OtherCitationDetails (str "U.S. Geological Survey, 1993, Digital Elevation"
                                                     " Models--data users guide 5:[Reston, Virginia],"
                                                     " U.S. Geological Survey, 48 p.")}
                         {:Creator "Solanki, S.K., I.G. Usoskin, B. Kromer, M. Schussler and J. Beer"
                          :OtherCitationDetails "DOC/NOAA/NESDIS/NGDC > National Geophysical Data Center, NESDIS, NOAA, U.S. Department of Commerce"}
                         {:Creator "Dublin Transport Office"
                          :OtherCitationDetails "Full report in PDF is available online"}]
   :DataCenters [{:Roles ["ARCHIVER" "DISTRIBUTOR"]
                  :ShortName "IRIS/PASSCAL"
                  :LongName "PASSCAL Instrument Center, Incorporated Research Institutions for Seismology"
                  :Uuid "10000000-0000-4000-a000-000000000000"
                  :ContactGroups [{:Roles ["Data Center Contact" "Technical Contact"]
                                   :Uuid "00000000-0000-4000-a000-000000000000"
                                   :ContactInformation {:RelatedUrls
                                                        {:Description "A sample related url description."
                                                         :URLContentType "CollectionURL"
                                                         :Type "GET DATA"
                                                         :Subtype "ECHO"
                                                         :URL "example-related-url-one.com"
                                                         :GetData {:Format "ascii"
                                                                   :MimeType "application/json"
                                                                   :Size "0"
                                                                   :Unit "KB"
                                                                   :Fees "None"
                                                                   :Checksum "SHA1-checksum"}
                                                         :GetService {:Format "binary"
                                                                      :MimeType "application/pdf"
                                                                      :Protocol "HTTP"
                                                                      :FullName "ContactGroups/ContactInformation/RelatedUrls/GetService/FullName"
                                                                      :DataID "Contact group contact infomation related url Data ID"
                                                                      :URI ["URI one" "URI two"]}}
                                                        :ServiceHours "9:00 AM to 5:00 PM Monday - Friday."
                                                        :ContactInstruction "Sample contact instructions."
                                                        :ContactMechanisms [{:Type "Email"
                                                                             :Value "sample-email-one@anywhere.com"}
                                                                            {:Type "Mobile"
                                                                             :Value "555-555-5555"}]
                                                        :Addresses [{:StreetAddresses ["15 Minte Drive" "5 Perry Hall Lane"]
                                                                     :City "Baltimore"
                                                                     :StateProvince "Maryland"
                                                                     :Country "USA"
                                                                     :PostalCode "21236"}]}
                                   :GroupName "White Marsh Institute of Health"}]
                  :ContactPersons [{:Roles ["Technical Contact" "Science Contact"]
                                    :Uuid "20000000-0000-4000-a000-000000000000"
                                    :FirstName "John"
                                    :LastName "Doe"
                                    :ContactInformation {:RelatedUrls
                                                         {:Description "A sample related url description."
                                                          :URLContentType "PublicationURL"
                                                          :Type "GET SERVICE"
                                                          :Subtype "EDG"
                                                          :URL "http://example-two.com"
                                                          :GetData {:Format "MODIS Tile SIN"
                                                                    :MimeType "application/x-hdf"
                                                                    :Size "1"
                                                                    :Unit "MB"
                                                                    :Fees "1000"
                                                                    :Checksum "Checksum"}
                                                          :GetService {:Format "binary"
                                                                       :MimeType "application/pdf"
                                                                       :Protocol "HTTP"
                                                                       :FullName "John Doe"
                                                                       :URI ["uri-1" "uri-2"]}}
                                                         :ContactMechanisms [{:Type "Email"
                                                                              :Value "sample-email-two@example.com"}
                                                                             {:Type "Mobile"
                                                                              :Value "666-666-6666"}]
                                                         :Addresses [{:StreetAddresses ["4 Cherrywood Lane" "8100 Baltimore Avenue"]
                                                                      :City "College Park"
                                                                      :StateProvince "MD"
                                                                      :Country "America"
                                                                      :PostalCode "20770"}]}}]}]
   :DirectoryNames [{:ShortName "directory-shortname-one"
                     :LongName "directory-longname-one"}
                    {:ShortName "directory-shortname-two"
                     :LongName "directory-longname-two"}]
   :CollectionDataType "NEAR_REAL_TIME"
   :DOI {:DOI "Dummy-DOI"}
   :EntryTitle "The collection entry title."
   :ShortName "VIIRS"
   :LongName "Visible Infrared Imaging Radiometer suite."
   :ISOTopicCategories ["elevation" "GEOSCIENTIFIC INFORMATION" "OCEANS"]
   :LocationKeywords [{:Category "CONTINENT"
                       :Type "NORTH AMERICA"
                       :Subregion1 "UNITED STATES OF AMERICA"
                       :Subregion2 "MICHIGAN"
                       :Subregion3 "DETROIT"
                       :DetailedLocation "MOUNTAIN"}
                      {:Category "OCEAN"
                       :Type "ATLANTIC OCEAN"
                       :Subregion1 "NORTH ATLANTIC OCEAN"
                       :Subregion2 "GULF OF MEXICO"
                       :DetailedLocation "WATER"}]
   :ProcessingLevel {:Id "4"}
   :TemporalKeywords ["Composit" "Annual" "Day"]
   :TilingIdentificationSystems [{:TilingIdentificationSystemName "MISR"
                                  :Coordinate1 {:MinimumValue 0
                                                :MaximumValue 10}
                                  :Coordinate2 {:MinimumValue 100
                                                :MaximumValue 150}}
                                 {:TilingIdentificationSystemName "CALIPSO"
                                  :Coordinate1 {:MinimumValue -10
                                                :MaximumValue 10}
                                  :Coordinate2 {:MinimumValue -50
                                                :MaximumValue -25}}]
   :Version "001"
   :VersionDescription "The beginning version of a sample collection."
   :Platforms [{:Type "In Situ Land-based Platforms"
                :ShortName "SURFACE WATER WIER"
                :LongName "In-situ-longname"
                :Characteristics [{:Name "characteristic-name-one"
                                   :Description "characteristic-description-one"
                                   :Value "256"
                                   :Unit "Meters"
                                   :DataType "INT"}]
                :Instruments [{:ShortName "LIDAR"
                               :LongName "Light Detection and Ranging"
                               :Characteristics [{:Name "characteristic-name-two"
                                                  :Description "characteristic-description-two"
                                                  :Value "1024.5"
                                                  :Unit "Inches"
                                                  :DataType "FLOAT"}]}
                              {:ShortName "WCMS"
                               :LongName "Water Column Mapping System"}]}]
   :Projects [{:ShortName "EOSDIS"
               :LongName "Earth Observing System Data Information System"}
              {:ShortName "GTOS"
               :LongName "Global Terrestrial Observing System"}
              {:ShortName "ESI"
               :LongName "Environmental Sustainability Index"}]
   :RelatedUrls [{:Description "Related-url description."
                  :URLContentType "PublicationURL"
                  :Type "GET SERVICE"
                  :Subtype "EDG"
                  :URL "related-url-example.com"}
                 {:Description "A test related url."
                  :URLContentType "DataCenterURL"
                  :Type "HOME PAGE"
                  :Subtype "GENERAL DOCUMENTATION"
                  :URL "related-url-example-two.com"}]
   :ContactPersons [{:Roles ["AUTHOR"]
                     :ContactInformation {:ContactMechanisms [{:Type "Email"
                                                               :Value "ncdc.orders at noaa.gov"}
                                                              {:Type "Telephone"
                                                               :Value "+1 828-271-4800"}]
                                          :Addresses [{:StreetAddresses ["151 Patton Avenue, Federal Building, Room 468"]
                                                       :City "Asheville"
                                                       :StateProvince "NC"
                                                       :Country "USA"
                                                       :PostalCode "28801-5001"}]}
                     :FirstName "Alice"
                     :MiddleName ""
                     :LastName "Bob"}]
   :ContactGroups [{:Roles ["SCIENCE CONTACT"]
                    :GroupName "TEAM SPOCK"
                    :LongName "VULCAN YET LIVES"
                    :Uuid "007c89f8-39ca-4645-b31a-d06a0118e8b2"
                    :NonServiceOrganizationAffiliation "TEAM KIRK"
                    :ContactInformation {:ContactMechanisms
                                         [{:Type "Email"
                                           :Value "custserv at usgs.gov"}
                                          {:Type "Fax"
                                           :Value "605-594-6589"}
                                          {:Type "Telephone"
                                           :Value "605-594-6151"}]
                                         :Addresses [{:StreetAddresses ["47914 252nd Street"]
                                                      :City "Sioux Falls"
                                                      :StateProvince "SD"
                                                      :Country "USA"
                                                      :PostalCode "57198-0001"}]}}]
   :ScienceKeywords [{:Category "EARTH SCIENCE SERVICES"
                      :Topic "DATA ANALYSIS AND VISUALIZATION"
                      :Term "GEOGRAPHIC INFORMATION SYSTEMS"}
                     {:Category "ATMOSPHERE"
                      :Topic "ATMOSPHERIC WINDS"
                      :Term "SURFACE WINDS"
                      :VariableLevel1 "SPECTRAL/ENGINEERING"
                      :VariableLevel2 "MICROWAVE"
                      :VariableLevel3 "MICROWAVE IMAGERY"
                      :DetailedVariable "RADAR"}
                     {:Category "SCIENCE CAT 3"
                      :Topic "SCIENCE TOPIC 3"
                      :Term "SCIENCE TERM 3"}]
   :ArchiveAndDistributionInformation
   {:FileDistributionInformation
    [{:FormatType "Native"
      :AverageFileSize nil
      :Fees nil
      :Format "netCDF4"
      :TotalCollectionFileSize nil
      :TotalCollectionFileSizeBeginDate nil
      :TotalCollectionFileSizeUnit nil
      :Description nil
      :AverageFileSizeUnit nil
      :Media nil}
     {:FormatType "Native"
      :AverageFileSize nil
      :Fees nil
      :Format "PDF"
      :TotalCollectionFileSize nil
      :TotalCollectionFileSizeBeginDate nil
      :TotalCollectionFileSizeUnit nil
      :Description nil
      :AverageFileSizeUnit nil
      :Media nil}]}})

(def long-field-collection
  {:CollectionCitations
   [{:Creator "Anita Risch, Martin Schuetz, Raul Ochoa-Hueso"
     :ReleasePlace "Birmensdorf, Switzerland"
     :Title "Ecosystem coupling and multifunctionality - exclosure experiment"
     :OnlineResource
     {:Linkage "https://www.envidat.ch/dataset/ecosystem-coupling-and-multifunctionality-exclosure-experiment"}
     :Publisher "Swiss Federal Institute for Forest, Snow and Landscape Research WSL"
     :ReleaseDate "2018-01-01T00:00:00.000Z"
     :Editor "Anita Risch"
     :DataPresentationForm "xlsx"
     :Version "1.0"}]
   :MetadataDates
   [{:Date "2019-04-14T19:16:58.800Z", :Type "CREATE"} {:Date "2020-09-08T10:27:02.038Z", :Type "UPDATE"}]
   :ISOTopicCategories ["environment"]
   :ShortName "ecosystem-coupling-and-multifunctionality-exclosure-experiment"
   :Abstract
   "This dataset contains all data on which the following publication below is based.    __Paper Citation:__   > Risch AC, Ochoa-Hueso R, van der Putten WH, Bump JK, Busse MD, Frey B, Gwiazdowicz DJ, Page-Dumroese DS, Vandegehuchte ML, Zimmermann S, Schütz M. Size-dependent loss of aboveground animals differentially affects grassland ecosystem coupling and functions. 2018. Nature Communications 9: 3684.  [doi: 10.1038/s41467-018-06105-4](https://doi.org/10.1038/s41467-018-06105-4).    Please cite this paper together with the citation for the datafile.    #Methods  ##Study sites   The experimental exclosure setups were installed within the SNP (IUCN category Ia preserve; Dudley 2008), in south-eastern Switzerland. The park covers 172 km2 of forests and subalpine and alpine grasslands along with scattered rock outcrops and scree slopes. The entire area has been protected from human impact (no hunting, fishing, camping or off-trail hiking) since 1914. Large, fairly homogenous patches of short- and tall-grass vegetation, which originate from different historical management and grazing regimes, cover the park’s subalpine grasslands entirely. Short-grass vegetation developed in areas where cattle used to rest (nutrient input) prior to the park’s foundation (14th century to 1914) (Schütz and others 2003, 2006) and is dominated by lawn grass species such as Festuca rubra L., Briza media L. and Agrostis capillaris L. (Schütz and others 2003, 2006). Today, this vegetation type is intensively grazed by diverse vertebrate and invertebrate communities that inhabit the park and consume up to 60% of the available biomass (Risch and others 2013). Tall-grass vegetation developed where cattle formerly grazed, but did not rest, and is dominated by rather nutrient-poor tussocks of Carex sempervirens Vill. and Nardus stricta L. (Schütz and others 2003, 2006). This vegetation type receives considerably less grazing, with only roughly 20% of the biomass consumed (Risch and others 2013). Consequently, the two vegetation types together represent a long-term trajectory of changes in grazing regimes. Underlying bedrock of all grasslands is dolomite, which renders these grasslands rather poor in nutrients regardless of former and current land-use regimes.  ##Experimental design  To progressively exclude aboveground vertebrate and invertebrate animals, we established 18 size-selective exclosure setups (nine in short-grass, nine in tall-grass vegetation) distributed over six subalpine grasslands across the SNP (Risch and others 2013, 2015). Elevation differences of exclosure locations did not exceed 350 m (between 1975 and 2300 m a.s.l.). The exclosures were established immediately after snowmelt in spring 2009 and were left in place for five consecutive growing seasons (until end of 2013). They were, however, temporarily dismantled every fall (late October after first snowfall) to protect them from avalanches. They were re-established in the same location every spring immediately after snowmelt. Each size-selective exclosure setup consisted of five plots (2 x 3 m) that progressively excluded aboveground vertebrates and invertebrates from large to small. The plots are labelled according to the guilds that had access to them “L/M/S/I”, “M/S/I”, “S/I”, “I”, “None”; L = large mammals, M = medium mammals, S = small mammals, I = invertebrates, None = no animals had access. As we only had permission to have the experimental setup in place for five consecutive growing seasons, the experiment had to be completely dismantled in the late fall of 2013 and all material removed from the SNP.  Our exclosure design was aimed at excluding mammalian herbivores, but naturally also excluded the few medium and small mammalian predators, as well as the entire aboveground invertebrate food web. A total of 26 large to small mammal species can be found in the SNP, but large apex predators are missing (wolf, bear, lynx) . Reptiles, amphibians and birds are scarce to absent in the subalpine grasslands under study. Only two reptile species occur in the park and they are confined to rocky areas that warm up enough for them to survive. One frog species spawns in an isolated pond far from our grasslands. Only three bird species occasionally feed on the subalpine grasslands. Using game cameras (Moultrie 6MP Game Spy I-60 Infrared Digital Game Camera, Moultrie Feeders, Alabaster, AL, USA), we did observe that the medium- and small-sized mammals (marmot/hares and mice) were not afraid to enter the fences and feed on their designated plots. We never spotted reptiles, amphibians or birds on camera. We distinguished between 59 higher aboveground-dwelling invertebrate taxa that our size-selective exclosures excluded (see also methods for aboveground-dwelling invertebrates below).   The “L/M/S/I” plot (not fenced) was located at least 5 m from the 2.1 m tall and 7 x 9 m large main electrical fence that enclosed the other four plots. The bottom wire of this fence was mounted at 0.5 m height and was not electrified to enable safe access for medium and small mammals, while fencing out the large ones. Within each main fence, we randomly established four 2 x 3 m plots separated by 1-m wide walkways from one another and from the main fence line: 1) the “M/S/I” plots were unfenced, allowing access to all but the large mammals; 2) the “S/I” plots (10 x 10 cm electrical mesh fence) excluded all medium-sized mammals. Note that the bottom 10 cm of this fence remained non-electrified to enable safe access for small mammals; 3) the “I” plots (2 x 2 cm metal mesh fence) excluded all mammals. We double-folded the mesh at the bottom 50 cm to reduce the mesh size to smaller than 1 x 1 cm openings; and 4) the “None” plots were surrounded by a 1 m tall mosquito net (1.5 x 2 mm) to exclude all animals. The top of the plot was covered with a mosquito-meshed wooden frame mounted to the corner posts (roof). We treated these plots a few times with biocompatible insecticide (Clean kill original, Eco Belle GmbH, Waldshut-Tiengen, Germany) to remove insects that might have entered during data collection or that hatched from the soil, but amounts were negligible and did not impact soil moisture conditions within these plots.  To assess whether the design of the “None” exclosure (mesh and roof) affected the response variables within the plots and, therefore, influenced the results, we established an additional six “micro-climate control” exclosures (one in each of the six grasslands) (Risch and others 2013, 2015). These exclosures were built as the “None” exclosures but were open at the bottom (20 cm) of the 3-m side of the fence facing away from the prevailing wind direction to allow invertebrates to enter. A 20-cm high and 3-m long strip of metal mesh was used to block access to small mammals. Thus, this construction allowed a comparable micro-climate to the “None” plots, but also a comparable feeding pressure by invertebrates to the “I” plots. We compared various properties within these exclosures against one another to assess if our construction altered the conditions in the “None” plots. We showed that differences in plant (e.g., vegetation height, aboveground biomass) and soil properties (e.g., soil temperature, moisture) found between the “I” and the “None” treatments were not due to the construction of the “None” exclosure, but a function of animal exclusions, although the amount of UV light reaching the plant canopy was significantly reduced (Risch and others 2013).   ##Aboveground invertebrate sampling  Aboveground invertebrates were sampled with two different methods to capture both ground- and plant-dwelling organisms: 1) we randomly placed two pitfall traps (67 mm in diameter, covered with a roof) filled with 20% propylene glycol in one 1 x 1 m subplot of the 2 x 3 m treatment plots in spring 2013 (May) and emptied them every two weeks until late September 2013 (Vandegehuchte and others 2017b, 2018). A pitfall trap consisted of a plastic cylinder (13 cm depth, 6.75 cm diameter). Within each cylinder we placed a 100 ml plastic vial with outer diameter 6.70 cm and on top of the cylinder we placed a plastic funnel to guide the invertebrates into the vials. Each trap was cover with a cone-shaped and transparent plastic roof to protect the trap from rain (Vandegehuchte and others 2017b, 2018). Note that in the “None” plots only one trap was placed as control to check for effectiveness of the exclosure. 2) We vacuumed all invertebrates from a 60 x 60 cm area on another 1 x 1 m subplot with a suction sampler (Vortis, Burkhard manufacturing CO, Ltd., Rickmansworth, Hertfordshire, UK) every month from June to September 2013 (Vandegehuchte and others 2017b, 2018). For this purpose, we quickly placed a square plastic frame (60 x 60 x 40 cm) with a closable mosquito mesh sleeve attached to the top edge into the plot from the outside. The suction sample was then inserted into through the sleeve and operated for 45 s to collect the invertebrates (Vandegehuchte and others 2017b, 2018).   We sorted the ≈100 000 individuals collected with both methods by hand and identified each individual morphologically to the lowest taxonomic level feasible (59 taxa, including orders, suborders, subfamilies, families; phylum for Mollusca). These taxa belonged to the following feeding types: 19 herbivores, 16 detritivores, 9 predators, 8 mixed feeders, 5 omnivores and 2 non-classified feeders (or not feeding as adults) (Vandegehuchte and others 2017b). We summed the numbers from the two pitfall traps and the suction sampling over the course of the 2013 season to represent the aboveground invertebrate abundance and community composition of a plot. Note: we did not specifically attempt to catch flying invertebrates with e.g., sticky traps, thus a few flying insects may have been missed with our vacuum sampling approach.  ##Sampling of plant properties   The vascular plant species composition was assessed at peak biomass every summer (July) by estimating the frequency of occurrence of each species with the pin count method in each plot (Frank and McNaughton 1990). A total of 172 taxa occurred within our 90 plots and we calculated plant species richness for each plot separately. We used the 2013 data in this study. Plant quality was assessed every year in July and September; here we use plant quality at the end of the experiment (September 2013). Two 10 x 100 cm wide strips of vegetation per plot were clipped, combined, dried at 65°C, and ground (Pulverisette 16, Fritsch, Idar-Oberstein, Germany) to pass through a 0.5 mm sieve. Twenty randomly selected samples across all treatments were analysed for N (Leco TruSpec Analyser, Leco, St. Joseph, Michigan, USA) (Vandegehuchte and others 2015). Nitrogen concentrations of the other samples were then estimated from models established for the experiment and the entire SNP relating Fourier transform-near infrared reflectance (FT-NIR) spectra to the measured values of N using a multi-purpose FT-NIR spectrometer (Bruker Optics, Fällanden, Switzerland) (Vandegehuchte and others 2015). Root biomass was sampled every fall by collecting five 2.2 cm diameter x 10 cm deep soil samples (Giddings Machine Company, Windsor, CO, USA) per plot (450 samples year-1). The samples were dried at 30 °C and roots were sorted from the sample by hand. We sorted each sample for 1 h which allowed to retrieve over 90% of all roots present in the samples (Risch and others 2013). The roots were then dried at 65 °C for 48 and weighed to the nearest mg. We averaged the values per plot and used the 2013 data only in this study.  ##Sampling of edaphic communities  In 2009, 2010, and 2011 we collected three composited soil samples (5 cm diameter x 10 cm depth; AMS Samplers, American Falls, ID, USA) and assessed bacterial community structure using T-RFLP profiling (Liu and others 1997; Blackwood and others 2003; Hodel and others 2014). We detected a total of 89 operational taxonomic units (OTUs). These values are in accordance with other studies reporting OTU richness (Wirthner and others 2011; Zumsteg and others 2012; Meola and others 2014) using T-RFLP profiling, a method that detects the most abundant, and thus likely, the most relevant, taxa. We averaged the data over the three years of collections for our calculations. Microbial biomass carbon (MBC) was determined with the substrate-induced method (Anderson and Domsch 1978) every fall (September) between 2009 and 2013 by collecting three mineral soil samples (5 cm diameter × 10 cm mineral soil core, AMS Samplers, American Falls, ID, USA). The three samples were combined (90 samples for each sampling year), immediately put on ice, taken to the laboratory, passed through a 2-mm sieve and stored at 4°C. Again, we only used the 2013 data in this study.  Soil samples (5 cm diameter x 10 cm depth) to extract soil arthropods were collected in June, July, and August 2011 with a soil corer lined with a plastic sleeve to ensure an undisturbed sample (total of 270 samples). The plastic line core was immediately sealed on both ends using cling film and put into a cooler. All plots were sampled within three days and the extraction of arthropods started the evening of the sampling day using a high-gradient Tullgren funnel apparatus (Crossley and Blair 1991; Vandegehuchte and others 2015). Samples were kept in the extractor for four days and the soil arthropods were collected in 95% ethanol. All individuals were counted and each individual was identified morphologically to the lowest level feasible [76 taxa, including orders, suborders, subfamilies, families (Protura, Thysanoptera, Aphidina, Psylina, Coleoptera, Brachycera, Nematocera, Auchenorryncha, Heteroptera, Formicidae); sub-phylum for Myriapoda, for Acari and Collembola also including morpho-species). Note that we also included larval stages (nine of the 76 taxa) (Vandegehuchte and others 2015). All data were summed over the season. A detailed species list for mites and collembolans is published (Vandegehuchte and others 2017a) [https://doi.org/10.1371/journal.pone.0118679.s001]. Earthworms are rare in the SNP and therefore were not included. We collected eight random 2.2 cm diameter x 10 cm deep soil cores from each plot in September 2013 to determine the soil nematode community composition. The samples were mixed and the nematodes were extracted from 100 ml of fresh soil using Oostenbrink elutriators (Oostenbrink 1960). All nematodes in a 1 ml of the 10 ml extract were counted, a minimum of 150 individuals sample-1 were identified to genus or family level using (Bongers 1988), the numbers of all nematodes were extrapolated to the entire sample and expressed for a 100 g dry sample. In total we identified 63 genus or family levels (Vandegehuchte and others 2015). The list of all the nematodes found is published (Vandegehuchte and others 2015) [http://www.oikosjournal.org/appendix/oik-03341] or DOI: [doi: 10.1111/oik.03341].   We are aware that sampling soil microbes from 2009 to 2011 and soil arthropods in 2011 was not ideal, but we are positive that this does not bias the results. Most of the parameters measured in our experiment either already showed a treatment response after the first growing season (e.g., plant biomass) or did not respond over the entire time experiment (e.g., microbial biomass C). The microbial community composition (2009 – 2011) was highly influenced by inter-annual differences in temperature and precipitation, but did not differ between treatments or vegetation types (Hodel and others 2014). We therefore felt comfortable using the 2009 through 2011 data for describing the soil microbial community in our experimental treatments. Similarly, we are positive that our soil arthropod data are representative. We did assess soil arthropods in August 2012 and found no differences to the August 2011 data. However, we did not feel comfortable combining the 2011 June, July, August data with only August data for 2012 for our analyses.   ##Sampling of soil properties  We collected three soil samples (5 cm diameter x 10 cm depth) in each plot in September 2013 after removing the vegetation. First, we collected the top layer of mineral soil rich in organic matter, the surface organic layer or rhizosphere, typically 1 to 3 cm in depth with a soil corer (AMS Samples, American Falls, Idaho, USA). Second, we collected a 10 cm mineral soil core beneath this surface layer. The cores for each layer were composited, dried at 65 °C for 48 h and fine-ground to pass a 0.5 mm screen. We then analysed all samples for total C using a Leco TruSpec Analyser (Leco, St. Joseph, Michigan, USA). Mineral soil pH was measured potentiometrically in 1:2 soil:CaCl2 solution with an equilibration time of 30 min.   Soil net N mineralisation was assessed during the 2013 growing season (Risch and others 2015). For this purpose, we randomly collected a 5 cm diameter x10 cm deep soil sample with a soil corer (AMS Samples, American Falls, Idaho, USA) after clipping the vegetation in June 2013. After weighing and sieving (4 mm mesh) the soil, we extracted a 20 g subsample in 1 mol l-1 KCl for 1.5 h on an end-over-end shaker and thereafter filtered it through ashless folded filter paper (DF 5895 150, ALBET LabScience, Hahnenmühle FineArt GmbH, Dassel, Germany). From these filtrates NO3- concentrations were measured colorimetrically (Norman and Stucki 1981) and NH4+with flow injection analysis (FIAS 300, Perkin Elmer, Waltham Massachusetts, USA) (Risch and others 2015). We dried the rest of the sample 105 °C to constant mass to determine fine,fraction bulk density. A second soil sample was collected within each plot in June 2013 with a corer lined with a 5 x 13 cm aluminium cylinder. The corer was driven 11.5 cm deep into the soil so that the top 1.5 cm of the cylinder remained empty. Into this space we placed a polyester bag (250 µm) filled an ion-exchanger resin to capture the incoming N. The bag was filled with a 1:1 mixture of acidic and alkaline exchanger resin (ion-exchanger I KA/ion exchanger IIIAA, Merck AG, Darmstadt, Germany). We then removed 1.5 cm soil at the bottom of the cylinder and placed a second resin exchanger bag into this space to capture the N leached from the soil column. To assure that the exchange resin was saturated with H+ and Cl- prior to filling the bags, the mixture was stirred with 1.2 ml l-1 HCl for 1 h and then rinsed with demineralized water until the electrical conductivity of the water reached 5 µm cm-1. The cylinder with the resin bags in place was reinserted into the soil with the top flush to the soil surface and incubated for three months. We recollected the cylinders in September 2013. Each resin bag and 20 g of sieved soil (4 mm mesh) from each cylinder were then separately extracted with KCl and NO3- and NH4+ concentrations were measured. Nitrate and NH4+ concentrations of all samples were then converted to a content basis by multiplying their values with fine fraction bulk density. Net N mineralisation was thereafter calculated as the difference between the N content of the samples collected at the end of the three-month incubation (including the N extracted from the bottom resin bag) and the N content at the beginning of the incubation (Risch and others 2015).   Soil CO2 emissions were measured every two weeks between 0900 and 1700 hrs from early May through late September 2013 with a PP-Systems SRC-1 soil respiration chamber (15 cm high, 10 cm diameter; closed circuit) attached to a PP-Systems EGM-4 infrared gas analyser (PP-Systems, Amesbury, MA, USA) on two locations per plot (Risch and others 2013). The chamber was placed on randomly placed, permanently installed PVC collars (10 cm diameter) driven 5 cm into the soil at the beginning of the study (Risch and others 2013). Freshly germinated plants growing within the collars were removed prior to each measurement to avoid measuring plant respiration or photosynthesis. The two measurements collected per plot and sampling date were averaged.   Soil moisture (with time domain reflectometry; Field-Scout TDR-100, Spectrum Technologies, Plainfield, Illionois, USA) and temperature (with a waterproof digital pocket thermometer; Barnstead International, Dubuque, Iowa, USA) were measured at five random locations per plot every two weeks during the growing seasons during the experiment for the 0 to 10 cm depth (Risch and others 2013, 2015). As soil moisture and soil temperature were highly negatively correlated (Risch and others 2013), we only used soil moisture for this study. We used plot-level averages of all values available to capture soil moisture variability during the five years of the experiment. The results remained unchanged when we only used soil moisture from the 2013 growing season.  ##Numeral calculations and statistical analyses  Ecosystem coupling. We conducted principal component analyses (PCAs; unscaled) at the complete dataset level using the abundances of each taxonomical entity to describe each of the five different communities used in this study: aboveground-dwelling invertebrates, vascular plants, soil microorganisms, soil arthropods and soil nematodes. We retained the first two components (PCA axis 1 and PCA axis 2) of each analysis as we found them to adequately represent the temporal and spatial variability of our 90 treatment plots in previous studies55,67. Together they explained a total of 71.70% of the variation for aboveground invertebrates, 44.36% for plants, 44.85% for soil microorganisms, 61.85% for soil arthropods and 77.19% for soil nematodes. In addition, we used soil pH and soil organic C content as a proxy for soil chemical properties, soil bulk density as a proxy for soil physical properties and soil moisture (negatively correlated with soil temperature) as a proxy for soil micro-climatic conditions for an overall total of fourteen constituents.  We calculated ecosystem coupling9 for each exclosure treatment within each vegetation type (i.e., 2  5 treatment combinations in total) as an integrated measure of pairwise ecological interactions between ecosystem constituents representing ecological communities and the soil abiotic environment. These ecological interactions are defined by non-parametric Spearman rank correlation analyses between two constituents, excluding interactions involving two abiotic constituents (e.g., soil pH vs. soil moisture) and interactions between the first (PC1) and second (PC2) component of each community type, as these are orthogonal by definition. Interactions between abiotic constituents were excluded from the analyses because the focus of our study was on communities and how they interact with one another and their surrounding environment; therefore, including abiotic-abiotic interactions was not of interest here. Given that the effectiveness of our experimental design resulted in that no community composition data of aboveground-dwelling invertebrates was available for the “None” plots (all animals excluded), only thirteen instead of fourteen constituents were included in the ecosystem coupling calculations for this treatment. The complete absence of aboveground invertebrates represents the most extreme case of disturbance between aboveground animal communities and the rest of the ecosystem constituents. This may have resulted in a slight overestimation of ecosystem coupling for these plots.   \tAverage ecosystem coupling was calculated as follows:   Ecosystem coupling=  where Xi is the absolute Coupling was calculated value of the Spearman’s rho coefficient of the ith correlation for each treatment within each vegetation type (i.e., based on nine replicates each), considering and n is the number of pairwise comparisons considered (n = a total of 80;  interactions (56 in the case of the “None” treatment). We considered a total of 40 biotic-biotic interactions (i.e., concerning two community-level principal components such as plants and microbes; 24 in the case of the “None” treatment) and 40 abiotic-biotic (i.e., concerning one community-level principal component and one abiotic factor, e.g., plant community and soil properties; 32 in the case of the “None” treatment).\tCoupling was calculated for each treatment within each vegetation type (i.e., based on nine replicates each), considering a total of 80 interactions (56 in the case of the “None” treatment). We considered a total of 40 biotic-biotic interactions (i.e., concerning two community-level principal components such as plants and microbes; 24 in the case of the “None” treatment) and 40 abiotic-biotic (i.e., concerning one community-level principal component and one abiotic factor, e.g., plant community and soil properties; 32 in the case of the “None” treatment).   To establish whether constituents were significantly and positively coupled within treatments (i.e., the average of their correlation coefficients were greater than in a null model where correlation only happens by chance), we calculated one-tailed p-values based on permutation tests with 999 permutations.        We considered six ecosystem functions and process rates commonly used to assess ecosystem functioning (Meyer and others 2015; Manning and others 2018). Plant N content represents a measure of forage quality, while plant richness has been shown to stabilise biomass production, thus allowing the system to respond to changes in herbivory. Soil net N mineralisation, soil respiration, root biomass, and microbial biomass represent fluxes or stocks of energy. For all functions and processes higher values represent higher functioning (Manning and others 2018). All these variables were measured in the last year of the experiment (2013). We then quantified ecosystem multifunctionality using the multiple threshold approach (Byrnes and others 2014; Manning and others 2018), which considers the number of functions that are above a certain threshold, over a series of threshold values (typically 10-99%) that are defined based on the maximum value of each function. We weighted all our functions equally for these calculations (Manning and others 2018). The number of functions in a plot with values higher than a given threshold value for the respective function is summed up. The sum represents ecosystem multifunctionality for that plot. Given that choosing any particular threshold as a measure of ecosystem multifunctionality is arbitrary, we calculated the average of thresholds from 10-90% (in 10% intervals) as a more integrated representation of ecosystem multifunctionality.   We used Pearson correlations to explore the relationships between ecosystem coupling (all interactions, biotic-biotic interactions, abiotic-biotic interactions involving above- and belowground constituents, and all interactions, biotic-biotic interactions, abiotic-biotic interactions involving belowground constituents only) and ecosystem multifunctionality by calculating the slopes of all relationships between ecosystem coupling and multifunctionality for all thresholds between 10 and 99%. We also related ecosystem coupling with the average of multifunctionality at thresholds between 30-80% as explained before and considered this correlation as a robust indication of the type of association between these two variables. In addition, we explored the relationships between ecosystem coupling (all interactions, biotic-biotic interactions, abiotic-biotic interactions involving above- and belowground constituents, and all interactions, biotic-biotic interactions, abiotic-biotic interactions involving belowground constituents only) and individual ecosystem functions. The effects of exclosures and vegetation type on individual functions and multifunctionality were evaluated using linear mixed effects models ('lme' function of the nlme package), with exclosure and vegetation type as fixed effects and fence as a random factor. All statistical analyses and numerical calculations were done in R version 3.4.0 (R Core Team 2016).    #References    - Anderson J, Domsch K. 1978. A physiological method for the quantitative measurement of microbial biomass in soil. Soil Biol Biochem 10:215–21.  - Blackwood CB, Marsh T, Kim S-H, Paul EA. 2003. Terminal Restriction Fragment Length Polymorphism Data Analysis for Quantitative Comparison of Microbial Communities. Appl Environ Microbiol 69:926–32. http://www.ncbi.nlm.nih.gov/pmc/articles/PMC143601/  - Bongers T. 1988. De nematoden von Nederland. Schoorl, The Netherlands: Pirola  - Byrnes JEK, Gamfeldt L, Isbell F, Lefcheck JS, Griffin JN, Hector A, Cardinale BJ, Hooper DU, Dee LE, Duffy JE. 2014. Investigating the relationship between biodiversity and ecosystem multifunctionality: Challenges and solutions. Methods Ecol Evol 5:111–24.  - Crossley DAJ, Blair JM. 1991. A high-efficiency low-technology Tulgren-type extractor for soil microarthopods. Agric Ecosyst Environ 34:187–92.  - Dudley N. 2008. Guidelines for applying protected area managment categories. Gland: IUCN  - Frank DA, McNaughton SJ. 1990. Aboveground biomass estimation with the canopy intercept method: A plant growth form caveat. Oikos 57:57–60.  - Haynes AG, Schütz M, Buchmann N, Page-Dumroese DS, Busse MD, Risch AC. 2014. Linkages between grazing history and herbivore exclusion on decomposition rates in mineral soils of subalpine grasslands. Plant Soil 374.  - Hodel M, Schütz M, Vandegehuchte ML, Frey B, Albrecht M, Busse MD, Risch AC. 2014. Does the aboveground herbivore assemblage influence soil bacterial community composition and richness in subalpine grasslands? Microb Ecol 68:584–95.  - Liu WT, Marsh TL, Cheng H, Forney LJ. 1997. Characterization of microbial diversity by determining terminal restriction fragment length polymorphisms of genes encoding 16S rRNA. Appl Environ Microbiol 63:4516–22. http://www.ncbi.nlm.nih.gov/pmc/articles/PMC168770/  - Manning P, van der Plas F, Soliveres S, Allan E, Maestre FT, Mace G, Whittingham MJ, Fischer M. 2018. Redefining ecosystem multifunctionality. Nat Ecol Evol 2:427–36. https://doi.org/10.1038/s41559-017-0461-7  - Meola M, Lazzaro A, Zeyer J. 2014. Diversity, resistance and resilience of the bacterial communities at two alpine glacier forefields after a reciprocal soil transplantation. Environ Microbiol 16:1918–34. https://onlinelibrary.wiley.com/doi/abs/10.1111/1462-2920.12435  - Meyer ST, Koch C, Weisser WW. 2015. Towards a standardized Rapid Ecosystem Function Assessment (REFA). Trends Ecol Evol 30:390–7. http://www.sciencedirect.com/science/article/pii/S0169534715000968  - Norman R., Stucki JW. 1981. The determination of nitrate and nitrite in soil extracts by ultraviolet spectrophotometry. Soil Sci Soc Am J 45:347–53.  - Ochoa-Hueso R. 2016. Non-linear disruption of ecological interactions in response to nitrogen deposition. Ecology 87:2802–2814.  - Oostenbrink M. 1960. Estimating nematode populations by some selected methods. In: Sasser NJ, Jenkins WR, editors. Nematology. Chapel Hill, NC, USA: University of North Carolina Press. pp 85–101.  - R Core Team. 2016. R: A language and environment for statistical computing. Vienna, Austria: R Foundation for Statistical Computing  - Risch AC, Haynes AG, Busse MD, Filli F, Schütz M. 2013. The response of soil CO2 fluxes to progressively excluding vertebrate and invertebrate herbivores depends on ecosystem type. Ecosystems 16:1192–202.  - Risch AC, Schütz M, Vandegehuchte ML, Van Der Putten WH, Duyts H, Raschein U, Gwiazdowicz DJ, Busse MD, Page-Dumroese DS, Zimmermann S. 2015. Aboveground vertebrate and invertebrate herbivore impact on net N mineralization in subalpine grasslands. Ecology 96:3312–22.  - Schütz M, Risch AC, Achermann G, Thiel-Egenter C, Page-Dumroese DS, Jurgensen MF, Edwards PJ. 2006. Phosphorus translocation by red deer on a subalpine grassland in the Central European Alps. Ecosystems 9:624–633.  - Schütz M, Risch AC, Leuzinger E, Krüsi BO, Achermann G. 2003. Impact of herbivory by red deer (Cervus elaphus L.) on patterns and processes in subalpine grasslands in the Swiss National Park. For Ecol Manage 181:177–88.  - Vandegehuchte ML, van der Putten WH, Duyts H, Schütz M, Risch AC. 2017a. Aboveground mammal and invertebrate exclusions cause consistent changes in soil food webs of two subalpine grassland types, but mechanisms are system-speciﬁc. Oikos 126:212–23.  - Vandegehuchte ML, Raschein U, Schütz M, Gwiazdowicz DJ, Risch AC. 2015. Indirect short- and long-term effects of aboveground invertebrate and vertebrate herbivores on soil microarthropod communities. PLoS One 10:e0118679.  - Vandegehuchte ML, Schütz M, de Schaetzen F, Risch AC. 2017b. Mammal-induced trophic cascades in invertebrate food webs are modulated by grazing intensity in subalpine grassland. J Anim Ecol 86:1434–46.  - Vandegehuchte ML, Trivellone V, Schütz M, Firn J, de Schaetzen F, Risch AC. 2018. Mammalian herbivores affect leafhoppers associated with specific plant functional types at different timescales. Funct Ecol 32:545–55.  - Wirthner S, Frey B, Busse MD, Schütz M, Risch AC. 2011. Effects of wild boar (Sus scrofa L.) rooting on the bacterial community structure in mixed-hardwood forest soils in Switzerland. Eur J Soil Biol 47:296–302. http://dx.doi.org/10.1016/j.ejsobi.2011.07.003  - Zumsteg A, Luster J, Göransson H, Smittenberg RH, Brunner I, Bernasconi SM, Zeyer J, Frey B. 2012. Bacterial, Archaeal and Fungal Succession in the Forefield of a Receding Glacier. Microb Ecol 63:552–64. https://doi.org/10.1007/s00248-011-9991-8  "
   :DOI {:DOI "doi:10.16904/envidat.44"}
   :RelatedUrls
   [{:URLContentType "VisualizationURL"
     :Type "GET RELATED VISUALIZATION"
     :URL "https://www.envidat.ch/envidat_thumbnail.png"}
    {:Subtype "GENERAL DOCUMENTATION"
     :URLContentType "PublicationURL"
     :Type "VIEW RELATED INFORMATION"
     :URL "https://www.envidat.ch/dataset/ecosystem-coupling-and-multifunctionality-exclosure-experiment"}]
   :DataDates [{:Date "2019-04-14T19:16:58.800Z", :Type "CREATE"} {:Date "2020-09-08T10:27:02.038Z", :Type "UPDATE"}]
   :ContactPersons
   [{:FirstName "Anita"
     :LastName "Risch"
     :Roles ["Technical Contact"]
     :ContactInformation {:ContactMechanisms [{:Value "anita.risch@wsl.ch", :Type "Email"}]}}]
   :AccessConstraints {:Description "Public access to the data"}
   :SpatialExtent
   {:HorizontalSpatialDomain
    {:Geometry
     {:BoundingRectangles
      [{:WestBoundingCoordinate 10.027084350585938
        :NorthBoundingCoordinate 46.76628424443288
        :EastBoundingCoordinate 10.395126342773438
        :SouthBoundingCoordinate 46.59480997531598}]
      :GPolygons
      [{:Boundary
        {:Points
         [{:Longitude 10.395126342773438, :Latitude 46.59480997531598}
          {:Longitude 10.395126342773438, :Latitude 46.76628424443288}
          {:Longitude 10.027084350585938, :Latitude 46.76628424443288}
          {:Longitude 10.027084350585938, :Latitude 46.59480997531598}
          {:Longitude 10.395126342773438, :Latitude 46.59480997531598}]}}]
      :CoordinateSystem "CARTESIAN"}}
    :GranuleSpatialRepresentation "CARTESIAN"}
   :ScienceKeywords
   [{:VariableLevel1 "SPECIES/POPULATION INTERACTIONS"
     :Category "EARTH SCIENCE"
     :Term "ECOLOGICAL DYNAMICS"
     :Topic "BIOSPHERE"}]
   :EntryTitle "Ecosystem coupling and multifunctionality - exclosure experiment"
   :CollectionProgress "COMPLETE"
   :UseConstraints
   {:Description
    "Usage constraintes defined by the license \"ODbL with Database Contents License (DbCL)\", see https://opendefinition.org/licenses/odc-odbl"}
   :AncillaryKeywords
   ["ECOSYSTEM COUPLING"
    "ECOSYSTEM FUNCTIONS"
    "ECOSYSTEM MULTIFUNCTIONALITY"
    "EXCLUSION"
    "GRASSLAND"
    "GRAZING"
    "INVERTEBRATES"
    "SUBALPINE"
    "SWISS NATIONAL PARK"
    "VERTEBRATES"]
   :ProcessingLevel {:Id "Not provided"}
   :Platforms [{:ShortName "Not provided"}]
   :Version "1.0"
   :TemporalExtents [{:SingleDateTimes ["2018-01-01T00:00:00.000Z"], :EndsAtPresentFlag false}]
   :DataCenters
   [{:ShortName "WSL"
     :ContactGroups
     [{:Roles ["Data Center Contact"]
       :GroupName "EnviDat"
       :ContactInformation {:ContactMechanisms [{:Value "envidat@wsl.ch", :Type "Email"}]}}]
     :Roles ["DISTRIBUTOR"]
     :LongName "Swiss Federal Institute for Forest, Snow and Landscape Research WSL"
     :ContactInformation
     {:RelatedUrls [{:URLContentType "DataCenterURL", :Type "HOME PAGE", :URL "https://www.wsl.ch"}]}}]
   :DataLanguage "eng"})

(deftest extract-collection-field-values
  (testing "very long Abstract strings are truncated"
    (is (= es/MAX_TEXT_UTF8_ENCODING_BYTES
           (count (.getBytes ((#'keyword-util/field-extract-fn :Abstract)
                              {:Abstract (string/join (repeat (* 2 es/MAX_TEXT_UTF8_ENCODING_BYTES)
                                                           "* "))})
                             "UTF-8")))))
  
  (are3 [field-key values]
    (is (= values
           ((#'keyword-util/field-extract-fn field-key) sample-umm-collection-concept)))

    "Abstract field"
    :Abstract
    "An abstract summary"

    "Associated DOIs"
    :AssociatedDOIs
    ["Associated-DOI-1" "Associated-DOI-2"]

    "DOI field"
    :DOI
    "Dummy-DOI"

    "EntryTitle field"
    :EntryTitle
    "The collection entry title."

    "LongName field"
    :LongName
    "Visible Infrared Imaging Radiometer suite."

    "ProcessingLevel field"
    :ProcessingLevel
    "4"

    "ShortName field"
    :ShortName
    "VIIRS"

    "Version field"
    :Version
    "001"

    "VersionDescription field"
    :VersionDescription
    "The beginning version of a sample collection."

    "AdditionalAttributes field"
    :AdditionalAttributes
    ["ALBEDOFILEID" "ID of the kernel albedo table used."
     "ASTERMapProjection" "The map projection of the granule"]

    "AncillaryKeywords field"
    :AncillaryKeywords
    ["LP DAAC" "EOSDIS" "USGS/EROS" "ESIP" "USGS" "LPDAAC" "(TMPA-RT)" "(USGS_EROS)" "(TMPA-RT-MULTI-TERM)"]

    "CollectionCitations field"
    :CollectionCitations
    ["Bowen Island Forest and Water Management Society (BIFWMS)"
     "U.S. Geological Survey, 1993, Digital Elevation Models--data users guide 5:[Reston, Virginia], U.S. Geological Survey, 48 p."
     "Solanki, S.K., I.G. Usoskin, B. Kromer, M. Schussler and J. Beer"
     "DOC/NOAA/NESDIS/NGDC > National Geophysical Data Center, NESDIS, NOAA, U.S. Department of Commerce"
     "Dublin Transport Office"
     "Full report in PDF is available online"]

    "CollectionDataType field"
    :CollectionDataType
    ["near_real_time" "nrt" "near real time","near-real time" "near-real-time" "near real-time"]

    "ContactGroups field"
    :ContactGroups
    ["TEAM SPOCK" "SCIENCE CONTACT"]

    "ContactMechanisms field"
    :ContactMechanisms
    ["ncdc.orders at noaa.gov" "custserv at usgs.gov" "sample-email-one@anywhere.com" "sample-email-two@example.com"]

    "ContactPersons field"
    :ContactPersons
    ["Alice" "Bob" "AUTHOR"]

    "DataCenters field"
    :DataCenters
    ["John" "Doe" "Technical Contact" "Science Contact" "White Marsh Institute of Health"
     "Data Center Contact" "Technical Contact" "IRIS/PASSCAL"]

    "ISOTopicCategories field"
    :ISOTopicCategories
    ["elevation" "GEOSCIENTIFIC INFORMATION" "OCEANS"]

    "LocationKeywords field"
    :LocationKeywords
    ["CONTINENT" "NORTH AMERICA" "UNITED STATES OF AMERICA" "MICHIGAN" "DETROIT" "MOUNTAIN"
     "OCEAN" "ATLANTIC OCEAN" "NORTH ATLANTIC OCEAN" "GULF OF MEXICO" "WATER"]

    "CollectionPlatforms field"
    :CollectionPlatforms
    ["characteristic-name-one" "characteristic-description-one" "256" "characteristic-name-two"
     "characteristic-description-two" "1024.5" "LIDAR" "WCMS" "SURFACE WATER WIER"]

    "Projects field"
    :Projects
    ["Earth Observing System Data Information System" "EOSDIS"
     "Global Terrestrial Observing System" "GTOS"
     "Environmental Sustainability Index" "ESI"]

    "RelatedUrls field"
    :RelatedUrls
    ["Related-url description." "EDG" "GET SERVICE" "related-url-example.com" "PublicationURL"
     "A test related url." "GENERAL DOCUMENTATION" "HOME PAGE" "related-url-example-two.com"
     "DataCenterURL"]

    "ScienceKeywords field"
    :ScienceKeywords
    ["EARTH SCIENCE SERVICES" nil "GEOGRAPHIC INFORMATION SYSTEMS" "DATA ANALYSIS AND VISUALIZATION"
     nil nil nil "ATMOSPHERE" "RADAR" "SURFACE WINDS" "ATMOSPHERIC WINDS" "SPECTRAL/ENGINEERING"
     "MICROWAVE" "MICROWAVE IMAGERY" "SCIENCE CAT 3" nil "SCIENCE TERM 3" "SCIENCE TOPIC 3" nil nil nil]

    "TilingIdentificationSystems field"
    :TilingIdentificationSystems
    ["MISR" "CALIPSO"]

    "TemporalKeywords field"
    :TemporalKeywords
    ["Composit" "Annual" "Day"]

    "Test getting the formats out of Archive and Distribution Information. The
     ArchiveFileInformation is nil, so it is testing that too."
    :ArchiveAndDistributionInformation
    ["netCDF4", "PDF"]))

(deftest concept-key->keywords
  (is (= ["Visible Infrared Imaging Radiometer suite."]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-collection-concept :LongName))))
  (is (= ["A test related url." "DataCenterURL" "EDG" "GENERAL DOCUMENTATION" "GET SERVICE" "HOME PAGE"
          "PublicationURL" "Related-url description." "related-url-example-two.com" "related-url-example.com"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-collection-concept :RelatedUrls))))
  (is (= ["ATMOSPHERE" "ATMOSPHERIC WINDS" "DATA ANALYSIS AND VISUALIZATION" "EARTH SCIENCE SERVICES" "GEOGRAPHIC INFORMATION SYSTEMS" "MICROWAVE" "MICROWAVE IMAGERY" "RADAR" "SCIENCE CAT 3" "SCIENCE TERM 3" "SCIENCE TOPIC 3" "SPECTRAL/ENGINEERING" "SURFACE WINDS"]
         (sort
          (keyword-util/concept-key->keywords
           sample-umm-collection-concept :ScienceKeywords)))))

(deftest concept-key->keyword-text
  (is (= (str "a com datacenterurl description description. documentation edg example general get "
              "home page publicationurl related related-url related-url-example-two.com "
              "related-url-example.com service test two url url.")
         (keyword-util/concept-key->keyword-text
          sample-umm-collection-concept :RelatedUrls)))
  (is (= (str "3 analysis and atmosphere atmospheric cat data earth engineering geographic imagery "
              "information microwave radar science services spectral spectral/engineering surface "
              "systems term topic visualization winds")
         (keyword-util/concept-key->keyword-text
          sample-umm-collection-concept :ScienceKeywords))))

(deftest concept-keys->keywords
  (let [schema-keys [:LongName
                     :ShortName
                     :Version]]
    (is (= ["001" "VIIRS" "Visible Infrared Imaging Radiometer suite."]
           (sort
            (keyword-util/concept-keys->keywords
             sample-umm-collection-concept schema-keys)))))
  (let [schema-keys [:LongName
                     :ShortName
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :RelatedUrls
                     :ScienceKeywords
                     :DataCenters]]
    (is (= ["(TMPA-RT)" "(TMPA-RT-MULTI-TERM)" "(USGS_EROS)" "001" "A test related url." "ATMOSPHERE"
            "ATMOSPHERIC WINDS" "AUTHOR" "Alice" "Bob" "DATA ANALYSIS AND VISUALIZATION" "Data Center Contact"
            "DataCenterURL" "Doe" "EARTH SCIENCE SERVICES" "EDG" "EOSDIS" "ESIP" "GENERAL DOCUMENTATION"
            "GEOGRAPHIC INFORMATION SYSTEMS" "GET SERVICE" "HOME PAGE" "IRIS/PASSCAL" "John"
            "LP DAAC" "LPDAAC" "MICROWAVE" "MICROWAVE IMAGERY" "PublicationURL" "RADAR"
            "Related-url description." "SCIENCE CAT 3" "SCIENCE CONTACT" "SCIENCE TERM 3"
            "SCIENCE TOPIC 3" "SPECTRAL/ENGINEERING" "SURFACE WINDS" "Science Contact" "TEAM SPOCK"
            "Technical Contact" "Technical Contact" "USGS" "USGS/EROS" "VIIRS"
            "Visible Infrared Imaging Radiometer suite." "White Marsh Institute of Health"
            "related-url-example-two.com" "related-url-example.com"]
           (sort
            (keyword-util/concept-keys->keywords
             sample-umm-collection-concept schema-keys))))))

(deftest concept-keys->keyword-text
  (let [schema-keys [:LongName
                     :ShortName
                     :Version]]
    (is (= "001 imaging infrared radiometer suite suite. viirs visible"
           (keyword-util/concept-keys->keyword-text
            sample-umm-collection-concept schema-keys))))
  (let [schema-keys [:ShortName
                     :ContactGroups]]
    (is (= "contact science spock team viirs"
           (keyword-util/concept-keys->keyword-text
            sample-umm-collection-concept schema-keys))))
  (let [schema-keys [:ContactPersons]]
    (is (= "alice author bob"
           (keyword-util/concept-keys->keyword-text
            sample-umm-collection-concept schema-keys))))
  (let [schema-keys [:LongName
                     :ShortName
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :RelatedUrls
                     :ScienceKeywords
                     :DataCenters]]
    (is (= (str "(tmpa-rt) (tmpa-rt-multi-term) (usgs_eros) 001 3 a alice analysis and atmosphere "
                "atmospheric author bob cat center com contact daac data datacenterurl description "
                "description. documentation doe earth edg engineering eosdis eros esip example "
                "general geographic get health home imagery imaging information infrared institute "
                "iris iris/passcal john lp lpdaac marsh microwave multi of page passcal publicationurl "
                "radar radiometer related related-url related-url-example-two.com related-url-example.com "
                "rt science service services spectral spectral/engineering spock suite suite. "
                "surface systems team technical term test tmpa tmpa-rt tmpa-rt-multi-term topic two "
                "url url. usgs usgs/eros usgs_eros viirs visible visualization white winds")
           (keyword-util/concept-keys->keyword-text
            sample-umm-collection-concept schema-keys)))))

(deftest create-keywords-field-test
  (let [keywords (ckw/create-keywords-field
                  "C1576922113-SCIOPS"
                  sample-umm-collection-concept
                  {:platform-long-names ["cc9.75"]
                   :instrument-long-names ["rmbrall"]
                   :entry-id "kw-fields-entry-id"})]
    (testing "returns a list of strings"
      (is (coll? keywords))
      (is (every? string? keywords)))
    (testing "the list does not contain duplicates"
      (is (distinct? keywords)))
    (testing "the list does not contain tabs"
      (is (empty? (mapcat #(re-find #"\t" %) keywords))))
    (testing "the list does not contain empty strings"
      (is (not-any? string/blank? keywords)))))
