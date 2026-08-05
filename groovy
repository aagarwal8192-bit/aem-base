import com.day.cq.search.PredicateGroup
import com.day.cq.search.QueryBuilder
import javax.jcr.Node
import javax.jcr.Property

def searchText = "Backup Plus"
def searchTokens = searchText.toLowerCase().split("\\s+")

def queryBuilder = getService(QueryBuilder)
def session = resourceResolver.adaptTo(javax.jcr.Session)

// ------------------------------------------------------------------
// Build same query as Asset Share Commons
// ------------------------------------------------------------------

Map<String, String> map = [
        "path"                    : "/content/dam/seagate/brand-portal",
        "type"                    : "dam:Asset",
        "fulltext"                : searchText,
        "p.limit"                 : "-1",
        "4_group.property"        : "jcr:content/contentFragment",
        "4_group.property.operation" : "not",
        "5_group.mainasset"       : "true"
]

def query = queryBuilder.createQuery(PredicateGroup.create(map), session)
def result = query.result

println "Total Hits : ${result.hits.size()}"
println()

// ------------------------------------------------------------------
// Read all indexed properties from damAssetLucene
// ------------------------------------------------------------------

def indexedProps = []

def indexRoot = session.getNode("/oak:index/damAssetLucene/indexRules/dam:Asset/properties")

indexRoot.nodes.each { propNode ->

    if(propNode.hasProperty("name")) {

        boolean analysed = propNode.hasProperty("analyzed") &&
                propNode.getProperty("analyzed").boolean

        boolean nodeScope = propNode.hasProperty("nodeScopeIndex") &&
                propNode.getProperty("nodeScopeIndex").boolean

        if(analysed || nodeScope) {
            indexedProps << propNode.getProperty("name").string
        }
    }
}

println "Indexed properties:"
indexedProps.each {
    println " - ${it}"
}

println()
println("==============================================================")
println()

// ------------------------------------------------------------------
// Check every hit
// ------------------------------------------------------------------

result.hits.each { hit ->

    def assetNode = session.getNode(hit.path)

    println assetNode.path

    indexedProps.each { propertyPath ->

        try {

            def fullPath = propertyPath.startsWith("/")
                    ? propertyPath.substring(1)
                    : propertyPath

            if(assetNode.hasProperty(fullPath)) {

                Property prop = assetNode.getProperty(fullPath)

                List<String> values = []

                if(prop.multiple) {
                    prop.values.each {
                        values << it.string
                    }
                } else {
                    values << prop.string
                }

                values.each { value ->

                    searchTokens.each { token ->

                        if(value?.toLowerCase()?.contains(token)) {

                            println "   ✓ ${token} -> ${propertyPath}"
                            println "      Value : ${value}"
                        }

                    }
                }

            }

        } catch(Exception ignored) {
            // Property doesn't exist on this asset
        }

    }

    println()
}
