(ns cmr.graph.queries.neo4j.demo.movie)

(def graph "MATCH (m:Movie)<-[:ACTED_IN]-(a:Person)
            RETURN m.title as movie, collect(a.name) as cast
            LIMIT {limit};")

(def search "MATCH (movie:Movie) WHERE movie.title =~ {title} RETURN movie;")

(def title "MATCH (movie:Movie {title:{title}})
            OPTIONAL MATCH (movie)<-[r]-(person:Person)
            RETURN movie.title as title,
                   collect({name:person.name,
                            job:head(split(lower(type(r)),'_')),
                            role:r.roles}) as cast LIMIT 1;")
