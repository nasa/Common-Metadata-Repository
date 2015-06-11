# virtual-product-app

The Virtual Product Application adds the concept of Virtual Products to the CMR. Virtual Products
represent products at a data provider that are generated on demand from users when they are ordered
or downloaded through a URL. A data provider will create a virtual product collection in the CMR.
Then within the Virtual Product App the virtual product will be configured with a source collection.
Whenever any granule is ingested in the source collection an equivalent ingest will be sent for each
of the virtual products that are configured with that source collection.

Virtual products will be supported during the ordering process as well. When a virtual product is
ordered ECHO will use the Virtual Product Apps API to convert virtual product order item ids into
the source ids and send them to the provider.

## Administrative API

### Administrative API Overview

  * /health
    * [GET - Gets the health of the virtual product application.](#health)

## License

Copyright © 2015 NASA
