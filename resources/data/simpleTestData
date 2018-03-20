# Collection Nodes
CREATE (c1:Collection {ShortName:"Collection 1"})
CREATE (c2:Collection {ShortName:"Collection 2"})
CREATE (c3:Collection {ShortName:"Collection 3"})
# URL Nodes
CREATE (u1:URL {Href:"http://cool.data"})
CREATE (u2:URL {Href:"http://cool.data/page"})
CREATE (u3:URL {Href:"http://cool.data/interesting"})
CREATE (u4:URL {Href:"http://cool.data/download"})
CREATE (u5:URL {Href:"http://cool.data/whitepaper"})
# Collection Relationships
CREATE (c1)-[:HAS]->(u1)
CREATE (c1)-[:HAS]->(u3)
CREATE (c2)-[:HAS]->(u4)
CREATE (c2)-[:HAS]->(u5)
# URL Relationships
CREATE (u1)-[:IS_IN]->(c1)
CREATE (u3)-[:IS_IN]->(c1)
CREATE (u4)-[:IS_IN]->(c2)
CREATE (u5)-[:IS_IN]->(c2)
