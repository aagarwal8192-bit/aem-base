import com.day.cq.search.QueryBuilder
import com.day.cq.search.PredicateGroup
import javax.jcr.Session
import javax.jcr.Property

def searchText = "Backup Plus"
def searchTokens = searchText.toLowerCase().split("\\s+")

Session session = resourceResolver.adaptTo(Session)
QueryBuilder queryBuilder = getService(QueryBuilder)

Map<String, String> predicates = [
        "path"     : "/content/dam/seagate/brand-portal",
        "type"     : "dam:Asset",
        "fulltext" : searchText,
        "p.limit"  : "-1"
]

def query = queryBuilder.createQuery(PredicateGroup.create(predicates), session)
def result = query.getResult()

println "Total Hits : ${result.getHits().size()}"
println "============================================================"

result.getHits().each { hit ->

    def asset = session.getNode(hit.getPath())

    println "\nAsset : ${asset.getPath()}"

    boolean found = false

    // Check asset name
    String assetName = asset.getName()

    searchTokens.each { token ->
        if (assetName.toLowerCase().contains(token)) {
            found = true
            println "MATCH : Asset Name"
            println "VALUE : ${assetName}"
        }
    }

    // Metadata
    if(asset.hasNode("jcr:content")) {

        def jc = asset.getNode("jcr:content")

        if(jc.hasNode("metadata")) {

            def metadata = jc.getNode("metadata")

            metadata.getProperties().each { Property prop ->

                try {

                    if(prop.getDefinition().isMultiple()) {

                        prop.getValues().each { v ->

                            String value = v.getString()

                            searchTokens.each { token ->

                                if(value &&
                                   value.toLowerCase().contains(token)) {

                                    found = true

                                    println "MATCH : ${prop.getName()}"
                                    println "VALUE : ${value}"
                                    println()
                                }
                            }
                        }

                    } else {

                        String value = prop.getString()

                        searchTokens.each { token ->

                            if(value &&
                               value.toLowerCase().contains(token)) {

                                found = true

                                println "MATCH : ${prop.getName()}"
                                println "VALUE : ${value}"
                                println()
                            }
                        }

                    }

                } catch(Exception e) {
                    // Ignore binary or unsupported properties
                }

            }
        }
    }

    if(!found) {
        println ">>> No obvious metadata match found."
    }

    println "------------------------------------------------------------"
}
