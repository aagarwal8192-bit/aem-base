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
 * Two situations are handled once a column container is found:
 *
 *  1. Multiple headings that individually fit within the cap are packed
 *     together into a column (bin-packed, kept intact) until the next
 *     heading would push the running total over the cap - then a new
 *     column starts.
 *
 *  2. A SINGLE heading whose own children already exceed the cap (e.g.
 *     BarraCuda with 20 pages, cap 16) is split directly:
 *       - the first chunk (up to maxPerColumn pages) stays wrapped under
 *         a clone of the original heading (keeps its title/link),
 *       - any remaining chunk(s) become column(s) containing the bare
 *         pages directly, with NO heading wrapper.
 */
public class NavigationColumnUtils {

    private static final int MAX_ITEMS_PER_COLUMN = 16;

    /** A node with no title/link is treated as a structural column container. */
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
     * as one page (counts as 1).
     */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> pagesOf(HashMap<String, Object> heading) {
        Object itemsObj = heading.get("items");
        return (itemsObj instanceof List) ? (List<HashMap<String, Object>>) itemsObj : null;
    }

    private static int countSecondChildPages(HashMap<String, Object> heading) {
        List<HashMap<String, Object>> pages = pagesOf(heading);
        return (pages != null) ? pages.size() : 1;
    }

    /** Wraps a group of intact heading nodes into a single new column. */
    private static HashMap<String, Object> buildColumnFromHeadings(List<HashMap<String, Object>> headings) {
        HashMap<String, Object> column = new HashMap<>();
        column.put("title", "");
        column.put("link", "");
        column.put("target", false);
        column.put("displayLeftNavigation", false);
        column.put("items", new ArrayList<>(headings));
        return column;
    }

    /** Wraps a group of bare pages (no heading) into a single new column. */
    private static HashMap<String, Object> buildColumnFromPages(List<HashMap<String, Object>> pages) {
        HashMap<String, Object> column = new HashMap<>();
        column.put("title", "");
        column.put("link", "");
        column.put("target", false);
        column.put("displayLeftNavigation", false);
        column.put("items", new ArrayList<>(pages));
        return column;
    }

    /**
     * Splits a single oversized heading (its own page count > maxPerColumn)
     * into one or more columns:
     *  - first column: clone of the heading with only the first
     *    maxPerColumn pages, so its title/link is preserved
     *  - subsequent column(s): bare pages, no heading wrapper
     */
    private static List<HashMap<String, Object>> splitOversizedHeading(
            HashMap<String, Object> heading, int maxPerColumn) {

        List<HashMap<String, Object>> result = new ArrayList<>();
        List<HashMap<String, Object>> pages = pagesOf(heading);
        if (pages == null) {
            // no children to split; treat the heading itself as a single page
            result.add(buildColumnFromHeadings(new ArrayList<>(List.of(heading))));
            return result;
        }

        boolean first = true;
        for (int i = 0; i < pages.size(); i += maxPerColumn) {
            List<HashMap<String, Object>> chunk =
                    new ArrayList<>(pages.subList(i, Math.min(i + maxPerColumn, pages.size())));

            if (first) {
                HashMap<String, Object> headingClone = new HashMap<>(heading);
                headingClone.put("items", chunk);
                result.add(buildColumnFromHeadings(new ArrayList<>(List.of(headingClone))));
                first = false;
            } else {
                result.add(buildColumnFromPages(chunk));
            }
        }
        return result;
    }

    /**
     * Distributes heading nodes into one or more columns such that each
     * column holds at most maxPerColumn total 2nd-child pages. Headings
     * that individually fit are bin-packed and kept whole. A heading that
     * alone exceeds the cap is split via splitOversizedHeading().
     */
    private static List<HashMap<String, Object>> distributeHeadingsIntoColumns(
            List<HashMap<String, Object>> headings, int maxPerColumn) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        List<HashMap<String, Object>> currentHeadings = new ArrayList<>();
        int currentCount = 0;

        for (HashMap<String, Object> heading : headings) {
            int headingCount = countSecondChildPages(heading);

            if (headingCount > maxPerColumn) {
                // flush whatever is pending, then split this heading on its own
                if (!currentHeadings.isEmpty()) {
                    columns.add(buildColumnFromHeadings(currentHeadings));
                    currentHeadings = new ArrayList<>();
                    currentCount = 0;
                }
                columns.addAll(splitOversizedHeading(heading, maxPerColumn));
                continue;
            }

            if (!currentHeadings.isEmpty() && currentCount + headingCount > maxPerColumn) {
                columns.add(buildColumnFromHeadings(currentHeadings));
                currentHeadings = new ArrayList<>();
                currentCount = 0;
            }

            currentHeadings.add(heading);
            currentCount += headingCount;
        }

        if (!currentHeadings.isEmpty()) {
            columns.add(buildColumnFromHeadings(currentHeadings));
        }

        return columns;
    }

    /**
     * Recursively walks a list of sibling nodes. Whenever a column
     * container is found, it is replaced by the one-or-more re-chunked
     * columns built from its headings. Non-column nodes are kept as-is,
     * with their own children recursively processed so column containers
     * at any depth are found.
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
