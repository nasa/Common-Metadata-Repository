exports.fetchCmrCollection = async (_conceptId, _token) => ({
  hits: 1,
  took: 6,
  items: [
    {
      meta: {
        'revision-id': 5,
        deleted: false,
        format: 'application/dif10+xml',
        'concept-id': 'C1237293909-SCIOPSTEST'
      },
      umm: {
        DataLanguage: 'eng',
        AncillaryKeywords: [
          'FOREST RESERVES',
          'NATIONAL FOREST INVENTORY',
          'NEAR-NATURAL FOREST',
          'SWITZERLAND',
          'TREE MORTALITY'
        ],
        CollectionCitations: [
          {
            Creator: 'Jeanne Portier, Jan Wunder, Golo Stadelmann, Jürgen Zell, Meinrad Abegg, Esther Thürig, Brigitte Rohner',
            Editor: 'Fabrizio Cioldi',
            DataPresentationForm: '.csv,csv',
            OnlineResource: {
              Linkage: 'https://www.envidat.ch/dataset/latent-reserves-in-the-swiss-nfi'
            },
            Publisher: 'EnviDat',
            Title: "'Latent reserves' within the Swiss NFI",
            ReleaseDate: '2020-01-01T00:00:00.000Z',
            Version: '1.0',
            ReleasePlace: 'Birmensdorf, Switzerland'
          }
        ],
        SpatialExtent: {
          HorizontalSpatialDomain: {
            Geometry: {
              CoordinateSystem: 'CARTESIAN',
              BoundingRectangles: [
                {
                  WestBoundingCoordinate: 5.95587,
                  NorthBoundingCoordinate: 47.80838,
                  EastBoundingCoordinate: 10.49203,
                  SouthBoundingCoordinate: 45.81802
                }
              ],
              GPolygons: [
                {
                  Boundary: {
                    Points: [
                      {
                        Longitude: 10.49203,
                        Latitude: 45.81802
                      },
                      {
                        Longitude: 10.49203,
                        Latitude: 47.80838
                      },
                      {
                        Longitude: 5.95587,
                        Latitude: 47.80838
                      },
                      {
                        Longitude: 5.95587,
                        Latitude: 45.81802
                      },
                      {
                        Longitude: 10.49203,
                        Latitude: 45.81802
                      }
                    ]
                  }
                }
              ]
            }
          },
          GranuleSpatialRepresentation: 'CARTESIAN'
        },
        CollectionProgress: 'COMPLETE',
        ScienceKeywords: [
          {
            Category: 'EARTH SCIENCE',
            Topic: 'BIOSPHERE',
            Term: 'FOREST SCIENCE',
            VariableLevel1: 'FOREST CONSERVATION'
          }
        ],
        TemporalExtents: [
          {
            EndsAtPresentFlag: false,
            SingleDateTimes: [
              '2020-01-01T00:00:00.000Z'
            ]
          }
        ],
        ProcessingLevel: {
          Id: 'Not provided'
        },
        DOI: {
          DOI: 'doi:10.16904/envidat.166'
        },
        ShortName: 'latent-reserves-in-the-swiss-nfi',
        EntryTitle: "'Latent reserves' within the Swiss NFI",
        ISOTopicCategories: [
          'environment'
        ],
        AccessConstraints: {
          Description: 'Access to the data upon request'
        },
        RelatedUrls: [
          {
            URLContentType: 'VisualizationURL',
            Type: 'GET RELATED VISUALIZATION',
            URL: 'https://www.envidat.ch/envidat_thumbnail.png'
          },
          {
            URLContentType: 'PublicationURL',
            Type: 'VIEW RELATED INFORMATION',
            Subtype: 'GENERAL DOCUMENTATION',
            URL: 'https://www.envidat.ch/dataset/latent-reserves-in-the-swiss-nfi'
          }
        ],
        DataDates: [
          {
            Date: '2020-07-29T14:18:59.791Z',
            Type: 'CREATE'
          },
          {
            Date: '2021-02-04T04:39:30.512Z',
            Type: 'UPDATE'
          }
        ],
        Abstract: "The files refer to the data used in Portier et al. \"‘Latent reserves’: a hidden treasure in National Forest Inventories\" (2020) *Journal of Ecology*.           **'Latent reserves'** are defined as plots in National Forest Inventories (NFI) that have been free of human influence for >40 to >70 years. They can be used to investigate and acquire a deeper understanding of attributes and processes of near-natural forests using existing long-term data. To determine which NFI sample plots could be considered ‘latent reserves’, criteria were defined based on the information available in the Swiss NFI database:           * Shrub forests were excluded.  * Plots must have been free of any kind of management, including salvage logging or sanitary cuts, for a minimum amount of time. Thresholds of 40, 50, 60 and 70 years without intervention were tested.  * To ensure that species composition was not influenced by past management, plots where potential vegetation was classified as deciduous by Ellenberg & Klötzli (1972) had to have an observed proportion of deciduous trees matching the theoretical proportion expected in a natural deciduous forest, as defined by Kienast, Brzeziecki, & Wildi (1994).  * Plots had to originate from natural regeneration.   * Intensive livestock grazing must never have occurred on the plots.          The tables stored here were derived from the first, second and third campaigns of the Swiss NFI. The raw data from the Swiss NFI can be provided free of charge within the scope of a contractual agreement (http://www.lfi.ch/dienstleist/daten-en.php).    ****    The files 'Data figure 2' to 'Data figure 8' are publicly available and contain the data used to produce the figures published in the paper.     The files 'Plot-level data for characterisation of 'latent reserves' and 'Tree-level data for characterisation of 'latent reserves' contain all the data required to reproduce the section of the article concerning the characterisation of 'latent reserves' and the comparison to managed forests. The file 'Data for mortality analyses' contains the data required to reproduce the section of the article concerning tree mortality in 'latent reserves'. The access to these three files is restricted as they contain some raw data from the Swiss NFI, submitted to the Swiss law and only accessible upon contractual agreement.   ",
        MetadataDates: [
          {
            Date: '2020-07-29T14:18:59.791Z',
            Type: 'CREATE'
          },
          {
            Date: '2021-02-04T04:39:30.512Z',
            Type: 'UPDATE'
          }
        ],
        Version: '1.0',
        UseConstraints: {
          Description: 'Usage constraintes defined by the license "WSL Data Policy", see https://www.wsl.ch/en/about-wsl/programmes-and-initiatives/envidat.html'
        },
        ContactPersons: [
          {
            Roles: [
              'Technical Contact'
            ],
            ContactInformation: {
              ContactMechanisms: [
                {
                  Type: 'Email',
                  Value: 'fabrizio.cioldi@wsl.ch'
                }
              ]
            },
            FirstName: 'Fabrizio',
            LastName: 'Cioldi'
          }
        ],
        DataCenters: [
          {
            Roles: [
              'DISTRIBUTOR'
            ],
            ShortName: 'WSL',
            LongName: 'Swiss Federal Institute for Forest, Snow and Landscape Research WSL',
            ContactGroups: [
              {
                Roles: [
                  'Data Center Contact'
                ],
                ContactInformation: {
                  ContactMechanisms: [
                    {
                      Type: 'Email',
                      Value: 'envidat@wsl.ch'
                    }
                  ]
                },
                GroupName: 'EnviDat'
              }
            ],
            ContactInformation: {
              RelatedUrls: [
                {
                  URLContentType: 'DataCenterURL',
                  Type: 'HOME PAGE',
                  URL: 'https://www.wsl.ch'
                }
              ]
            }
          }
        ],
        Platforms: [
          {
            ShortName: 'Not provided'
          }
        ]
      }
    }
  ]
})
