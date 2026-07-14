package com.seagate.brandportal.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms the navigation tree so that, for a given level-1 item, ALL of
 * its level-4 pages (across every level-3 heading, across every original
 * column container) are packed continuously into new columns capped at
 * MAX_ITEMS_PER_COLUMN pages each.
 *
 * Key behaviors:
 *  - Column containers can appear at any depth (level-2 directly under a
 *    level-1 item, or deeper e.g. level-3 under an intermediate
 *    "Category" node) - located recursively.
 *  - If a level-1 item has MULTIPLE column containers, all their level-3
 *    headings are combined into one continuous sequence before packing -
 *    the column boundary is not restarted per original column container.
 *  - Headings are packed in order. A heading that fits in the remaining
 *    space of the current column is added whole, keeping its title.
 *  - A heading that does not fully fit is split: the portion that fits
 *    fills out the current column (still labeled with the heading's
 *    title, since it's that heading's FIRST chunk), then a new column is
 *    opened and the remainder continues - any chunk after the first for
 *    the same heading is added WITHOUT a title (empty), since it's a
 *    continuation, not a new heading.
 *  - Packing keeps flowing across heading boundaries: once a heading is
 *    exhausted, the next heading continues filling the same (still open)
 *    column if there's remaining capacity.
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

    /** Returns a heading's own children (the level-4 pages), or null if it has none. */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> pagesOf(HashMap<String, Object> heading) {
        Object itemsObj = heading.get("items");
        return (itemsObj instanceof List) ? (List<HashMap<String, Object>>) itemsObj : null;
    }

    /** Wraps a list of level-3 nodes (full headings, partial headings, or continuation chunks) into a new column. */
    private static HashMap<String, Object> buildColumn(List<HashMap<String, Object>> level3Nodes) {
        HashMap<String, Object> column = new HashMap<>();
        column.put("title", "");
        column.put("link", "");
        column.put("target", false);
        column.put("displayLeftNavigation", false);
        column.put("items", new ArrayList<>(level3Nodes));
        return column;
    }

    /** Builds a continuation node (no title) wrapping a chunk of pages. */
    private static HashMap<String, Object> buildContinuationNode(List<HashMap<String, Object>> chunk) {
        HashMap<String, Object> node = new HashMap<>();
        node.put("title", "");
        node.put("link", "");
        node.put("target", false);
        node.put("displayLeftNavigation", false);
        node.put("items", chunk);
        return node;
    }

    /**
     * Continuously packs a flat, ordered sequence of level-3 headings into
     * new columns, splitting any heading whose pages don't fully fit in
     * the remaining space of the current column, and carrying leftover
     * capacity forward into subsequent headings.
     */
    private static List<HashMap<String, Object>> packHeadingsIntoColumns(
            List<HashMap<String, Object>> headings, int maxPerColumn) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        List<HashMap<String, Object>> currentColumnItems = new ArrayList<>();
        int currentCount = 0;

        for (HashMap<String, Object> heading : headings) {
            List<HashMap<String, Object>> pages = pagesOf(heading);

            if (pages == null) {
                // Leaf heading with no children - treat as a single unit.
                if (!currentColumnItems.isEmpty() && currentCount + 1 > maxPerColumn) {
                    columns.add(buildColumn(currentColumnItems));
                    currentColumnItems = new ArrayList<>();
                    currentCount = 0;
                }
                currentColumnItems.add(heading);
                currentCount += 1;
                continue;
            }

            int totalPages = pages.size();
            int offset = 0;
            boolean first = true;

            if (totalPages == 0) {
                // Heading with an empty items list - keep it as-is, counts as 0.
                currentColumnItems.add(new HashMap<>(heading));
                continue;
            }

            while (offset < totalPages) {
                int capacityLeft = maxPerColumn - currentCount;
                if (capacityLeft <= 0) {
                    columns.add(buildColumn(currentColumnItems));
                    currentColumnItems = new ArrayList<>();
                    currentCount = 0;
                    capacityLeft = maxPerColumn;
                }

                int take = Math.min(capacityLeft, totalPages - offset);
                List<HashMap<String, Object>> chunk = new ArrayList<>(pages.subList(offset, offset + take));

                HashMap<String, Object> node;
                if (first) {
                    // First chunk of this heading - keep its title/link/etc.
                    node = new HashMap<>(heading);
                    node.put("items", chunk);
                } else {
                    // Continuation of an already-started heading - no title.
                    node = buildContinuationNode(chunk);
                }

                currentColumnItems.add(node);
                currentCount += chunk.size();
                offset += chunk.size();
                first = false;
            }
        }

        if (!currentColumnItems.isEmpty()) {
            columns.add(buildColumn(currentColumnItems));
        }

        return columns;
    }

    /**
     * Recursively walks a list of sibling nodes. All column containers
     * found among the given siblings (there may be more than one) have
     * their headings combined into a single continuous sequence, which is
     * then packed into new columns via packHeadingsIntoColumns(). The new
     * columns are inserted at the position of the first column container
     * found; non-column siblings are kept in their original relative
     * order, and recursed into so column containers at deeper levels
     * (e.g. under an intermediate "Category" node) are also found.
     */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> processChildren(
            List<HashMap<String, Object>> children, int maxPerColumn) {

        List<HashMap<String, Object>> result = new ArrayList<>();
        if (children == null) {
            return result;
        }

        List<HashMap<String, Object>> combinedHeadings = new ArrayList<>();
        int firstColumnInsertIndex = -1;

        for (HashMap<String, Object> child : children) {
            if (isColumnContainer(child)) {
                if (firstColumnInsertIndex == -1) {
                    firstColumnInsertIndex = result.size();
                }
                Object headingsObj = child.get("items");
                if (headingsObj instanceof List) {
                    combinedHeadings.addAll((List<HashMap<String, Object>>) headingsObj);
                }
                // the original column container itself is dropped - it will
                // be replaced by the newly packed columns below.
            } else {
                HashMap<String, Object> clone = new HashMap<>(child);
                Object nestedItemsObj = child.get("items");
                if (nestedItemsObj instanceof List) {
                    clone.put("items", processChildren((List<HashMap<String, Object>>) nestedItemsObj, maxPerColumn));
                }
                result.add(clone);
            }
        }

        if (!combinedHeadings.isEmpty()) {
            List<HashMap<String, Object>> newColumns = packHeadingsIntoColumns(combinedHeadings, maxPerColumn);
            result.addAll(firstColumnInsertIndex, newColumns);
        }

        return result;
    }

    /**
     * Entry point: transforms the full navigation root map produced by
     * NavigationUtils.getNavigationItems() -&gt; { "items": [ ... ] }.
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
