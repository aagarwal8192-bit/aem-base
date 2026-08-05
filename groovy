import com.day.cq.search.PredicateGroup
import com.day.cq.search.QueryBuilder
import javax.jcr.Session

def searchText = "Backup Plus"
def searchTokens = searchText.toLowerCase().split("\\s+")

def queryBuilder = getService(QueryBuilder)
def session = resourceResolver.adaptTo(Session)

Map<String, String> map = [
    "path"      : "/content/dam/seagate/brand-portal",
    "type"      : "dam:Asset",
    "fulltext"  : searchText,
    "p.limit"   : "-1"
]

def query = queryBuilder.createQuery(PredicateGroup.create(map), session)
def result = query.result

println "Hits: ${result.hits.size()}"
println "===================================================="

def properties = [
    "dc:title",
    "dc:description",
    "cq:tags",
    "predictedTags",
    "predictedTagsConfidence",

    // Seagate metadata
    "seagateKeywords",
    "seagateBrand",
    "seagateProduct",
    "seagateProductType",
    "seagateProductCategory",
    "seagateNotes",
    "seagateAssetCategory",
    "seagateAssetType",
    "jcr:title",
    "jcr:description"
]

result.hits.each { hit ->

    def asset = session.getNode(hit.path)
    boolean found = false

    println "\nAsset : ${asset.path}"

    // Check asset name
    def assetName = asset.name
    searchTokens.each { token ->
        if (assetName.toLowerCase().contains(token)) {
            found = true
            println "  ✓ Asset Name : ${assetName}"
        }
    }

    // Check metadata
    if (asset.hasNode("jcr:content/metadata")) {

        def metadata = asset.getNode("jcr:content/metadata")

        properties.each { propertyName ->

            if (metadata.hasProperty(propertyName)) {

                def prop = metadata.getProperty(propertyName)

                def values = prop.multiple ?
                        prop.values.collect { it.string } :
                        [prop.string]

                values.each { value ->

                    if (value == null) return

                    searchTokens.each { token ->

                        if (value.toLowerCase().contains(token)) {

                            found = true

                            println "  ✓ Property : ${propertyName}"
                            println "    Matched  : ${token}"
                            println "    Value    : ${value}"
                        }
                    }
                }
            }
        }
    }

    if (!found) {
        println "  >>> No metadata match found."
    }

    println "----------------------------------------------------"
}
