import com.day.cq.search.PredicateGroup
import com.day.cq.search.QueryBuilder
import javax.jcr.Binary

def searchText = "Backup Plus"
def searchTokens = searchText.toLowerCase().split("\\s+")

def queryBuilder = getService(QueryBuilder)
def session = resourceResolver.adaptTo(javax.jcr.Session)

Map<String, String> map = [
        "path" : "/content/dam/seagate/brand-portal",
        "type" : "dam:Asset",
        "fulltext" : searchText,
        "p.limit" : "-1"
]

def query = queryBuilder.createQuery(PredicateGroup.create(map), session)
def result = query.result

println "Hits : ${result.hits.size()}"
println "====================================================="

// Metadata properties to inspect
def properties = [

    // OOTB
    "jcr:content/metadata/dc:title",
    "jcr:content/metadata/dc:description",
    "jcr:content/metadata/cq:tags",
    "jcr:content/metadata/predictedTags",
    "jcr:content/metadata/predictedTagsConfidence",

    // Custom Seagate
    "jcr:content/metadata/seagateKeywords",
    "jcr:content/metadata/seagateBrand",
    "jcr:content/metadata/seagateProduct",
    "jcr:content/metadata/seagateProductType",
    "jcr:content/metadata/seagateProductCategory",
    "jcr:content/metadata/seagateNotes",
    "jcr:content/metadata/seagateAssetCategory",
    "jcr:content/metadata/seagateAssetType",
    "jcr:content/metadata/jcr:title",
    "jcr:content/metadata/jcr:description"
]

result.hits.each { hit ->

    def asset = session.getNode(hit.path)

    boolean found = false

    println "\n${asset.path}"

    properties.each { propPath ->

        if (asset.hasProperty(propPath)) {

            def prop = asset.getProperty(propPath)

            def values = prop.multiple ?
                    prop.values.collect { it.string } :
                    [prop.string]

            values.each { value ->

                searchTokens.each { token ->

                    if (value?.toLowerCase()?.contains(token)) {

                        found = true

                        println "   ✓ ${token}"
                        println "      Property : ${propPath}"
                        println "      Value    : ${value}"
                    }
                }
            }
        }
    }

    // Check extracted binary text
    try {

        def dataPath = "jcr:content/renditions/original/jcr:content/jcr:data"

        if (asset.hasProperty(dataPath)) {

            Binary binary = asset.getProperty(dataPath).binary

            String text = binary.stream.getText("UTF-8")

            searchTokens.each { token ->

                if (text.toLowerCase().contains(token)) {

                    found = true

                    println "   ✓ ${token}"
                    println "      Property : Binary Extracted Text"
                }
            }

            binary.dispose()
        }

    } catch(Exception ignored) {
        // Images usually won't have readable extracted text
    }

    if(!found) {
        println "   >>> No metadata match found."
        println "   >>> Most likely matched by Lucene analysis, OCR, filename, or another indexed property."
    }
}
