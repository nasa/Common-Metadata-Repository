# Background

The [Common Metadata Repository][cmr-project] (CMR) is a high-performance,
high-quality, continuously evolving metadata system that catalogs all data and
service metadata records for the [EOSDIS][eosdis] system and will be the
authoritative management system for all EOSDIS metadata. These metadata records
are registered, modified, discovered, and accessed through programmatic
interfaces leveraging standard protocols and APIs.

With CMR firmly established in its production release cycles, interest has
grown in developing native desktop and mobile applications, command-line
tools, etc. CMR originally intended to generate multiple clients from its
swagger API documentation, but results have been mixed and this is not
currently maintained.

Due to the fact that CMR is written in Clojure (and the CMR developers love
writing in Clojure), creating a Clojure/Script client was a very natural path
to explore (ultimately more fulfilling for Clojure devs than tweaking Swagger
configurations/schemas). The fact that three clients are produced for the
price of one is a welcome bonus.

Code for the CMR is up on [github][cmr-github].

<!-- Named page links below: /-->

[cmr-project]: https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository
[eosdis]: https://earthdata.nasa.gov/about
[cmr-github]: https://github.com/nasa/Common-Metadata-Repository
