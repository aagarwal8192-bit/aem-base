package com.seagate.brandportal.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms the navigation tree so that wherever a "column container"
 * node is found (an empty title/link structural node, which may appear
 * at level-2 directly under a level-1 item, or deeper e.g. level-3 under
 * an intermediate "Category" node), its heading children get distributed
 * into one or more new column containers capped at MAX_ITEMS_PER_COLUMN
 * total 2nd-child pages each.
 *
 * Example tree shapes handled:
 *
 *   Assets (level1)
 *     -> Column Container (level2)          <-- found directly
 *          -> BarraCuda (level3, heading)
 *               -> BarraCuda 540 SSD (level4, page)
 *
 *   Assets (level1)
 *     -> Category (level2, real node, has a title)
 *          -> Column Container (level3)     <-- found one level deeper
 *               -> BarraCuda (level4, heading)
 *                    -> BarraCuda 540 SSD (level5, page)
 *
 * The column container is located by recursively walking the tree (no
 * assumption about which level it sits at). Once found, its own children
 * (the headings) are grouped into new column containers such that each
 * new column holds at most maxPerColumn total "2nd child" pages, i.e. the
 * sum of each heading's own children. Headings are kept intact within a
 * column; a new column starts once adding the next heading would exceed
 * the cap.
 */
public class NavigationColumnUtils {

    private static final int MAX_ITEMS_PER_COLUMN = 16;

    /**
     * A node with no title/link is treated as a structural column
     * container.
     */
    private static boolean isColumnContainer(Map<String, Object> node) {
        Object title = node.get("title");
        Object link = node.get("link");
        boolean emptyTitle = title == null || title.toString().isEmpty();
        boolean emptyLink = link == null || link.toString().isEmpty();
        return emptyTitle && emptyLink;
    }

    /**
     * Counts the "2nd child" pages for a heading node, i.e. the heading's
     * own children. If the heading has no children, it is itself treated
     * as one page (counts as 1) so it isn't lost from the count.
     */
    private static int countSecondChildPages(HashMap<String, Object> heading) {
        Object itemsObj = heading.get("items");
        if (itemsObj instanceof List) {
            return ((List<?>) itemsObj).size();
        }
        return 1;
    }

    /** Builds a synthetic column wrapper around a group of heading nodes. */
    private static HashMap<String, Object> buildColumn(List<HashMap<String, Object>> headings) {
        HashMap<String, Object> column = new HashMap<>();
        column.put("title", "");
        column.put("link", "");
        column.put("target", false);
        column.put("displayLeftNavigation", false);
        column.put("items", new ArrayList<>(headings));
        return column;
    }

    /**
     * Distributes heading nodes into one or more columns such that each
     * column holds at most maxPerColumn total 2nd-child pages. Headings
     * are kept whole within a column.
     */
    private static List<HashMap<String, Object>> distributeHeadingsIntoColumns(
            List<HashMap<String, Object>> headings, int maxPerColumn) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        List<HashMap<String, Object>> currentHeadings = new ArrayList<>();
        int currentCount = 0;

        for (HashMap<String, Object> heading : headings) {
            int headingCount = countSecondChildPages(heading);

            if (!currentHeadings.isEmpty() && currentCount + headingCount > maxPerColumn) {
                columns.add(buildColumn(currentHeadings));
                currentHeadings = new ArrayList<>();
                currentCount = 0;
            }

            currentHeadings.add(heading);
            currentCount += headingCount;
        }

        if (!currentHeadings.isEmpty()) {
            columns.add(buildColumn(currentHeadings));
        }

        return columns;
    }

    /**
     * Recursively walks a list of sibling nodes. Whenever a column
     * container is found, it is replaced (in place, in its parent's
     * items list) by the one-or-more re-chunked columns built from its
     * headings. Non-column nodes are kept as-is, with their own children
     * recursively processed so column containers at any depth are found.
     */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> processChildren(
            List<HashMap<String, Object>> children, int maxPerColumn) {

        List<HashMap<String, Object>> result = new ArrayList<>();
        if (children == null) {
            return result;
        }

        for (HashMap<String, Object> child : children) {
            if (isColumnContainer(child)) {
                Object headingsObj = child.get("items");
                List<HashMap<String, Object>> headings =
                        (headingsObj instanceof List) ? (List<HashMap<String, Object>>) headingsObj : new ArrayList<>();
                result.addAll(distributeHeadingsIntoColumns(headings, maxPerColumn));
            } else {
                HashMap<String, Object> clone = new HashMap<>(child);
                Object nestedItemsObj = child.get("items");
                if (nestedItemsObj instanceof List) {
                    clone.put("items", processChildren((List<HashMap<String, Object>>) nestedItemsObj, maxPerColumn));
                }
                result.add(clone);
            }
        }

        return result;
    }

    /**
     * Entry point: transforms the full navigation root map produced by
     * NavigationUtils.getNavigationItems() -&gt; { "items": [ ... ] }.
     * Column containers are located recursively at whatever depth they
     * occur.
     */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> transformNavigation(
            HashMap<String, Object> root, int maxPerColumn) {

        HashMap<String, Object> output = new HashMap<>(root);
        Object itemsObj = root.get("items");
        if (itemsObj instanceof List) {
            output.put("items", processChildren((List<HashMap<String, Object>>) itemsObj, maxPerColumn));
        }
        return output;
    }

    /** Overload using the default 16-item cap. */
    public static HashMap<String, Object> transformNavigation(HashMap<String, Object> root) {
        return transformNavigation(root, MAX_ITEMS_PER_COLUMN);
    }
}
