package com.seagate.brandportal.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms the navigation tree so that, for a given level-1 item, its
 * level-3 headings are arranged into columns based on that level-1 item's
 * "layout" setting:
 *
 *  - "vertical" (default): all level-4 pages across every level-3 heading
 *    (and across every original column container) are packed CONTINUOUSLY
 *    into new columns capped at MAX_ITEMS_PER_COLUMN pages each. A
 *    heading that doesn't fully fit is split across columns; its first
 *    chunk keeps the heading's title, later chunks are unlabeled
 *    continuations. Packing flows across heading and column-container
 *    boundaries.
 *
 *  - "horizontal": no 16-item cap applies. Each level-3 heading simply
 *    becomes its own column, in order, regardless of how many level-4
 *    pages it has.
 *
 * Column containers (structural nodes with empty title/link) can appear
 * at any depth (e.g. level-2 directly under a level-1 item, or deeper,
 * such as level-3 under an intermediate "Category" node) - they are
 * located recursively. The level-1 item's layout choice is threaded down
 * through that recursion so every column container under it is arranged
 * consistently.
 */
public class NavigationColumnUtils {

    private static final int MAX_ITEMS_PER_COLUMN = 16;
    private static final String LAYOUT_KEY = "layout";
    private static final String LAYOUT_HORIZONTAL = "horizontal";

    /** A node with no title/link is treated as a structural column container. */
    private static boolean isColumnContainer(Map<String, Object> node) {
        Object title = node.get("title");
        Object link = node.get("link");
        boolean emptyTitle = title == null || title.toString().isEmpty();
        boolean emptyLink = link == null || link.toString().isEmpty();
        return emptyTitle && emptyLink;
    }

    private static boolean isHorizontal(Map<String, Object> level1Item) {
        Object layout = level1Item.get(LAYOUT_KEY);
        return layout != null && LAYOUT_HORIZONTAL.equalsIgnoreCase(layout.toString());
    }

    /** Returns a heading's own children (the level-4 pages), or null if it has none. */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> pagesOf(HashMap<String, Object> heading) {
        Object itemsObj = heading.get("items");
        return (itemsObj instanceof List) ? (List<HashMap<String, Object>>) itemsObj : null;
    }

    /** Wraps a list of level-3 nodes into a new column. */
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
     * VERTICAL layout: continuously packs a flat, ordered sequence of
     * level-3 headings into new columns, splitting any heading whose
     * pages don't fully fit in the remaining space of the current column,
     * and carrying leftover capacity forward into subsequent headings.
     */
    private static List<HashMap<String, Object>> packHeadingsVertically(
            List<HashMap<String, Object>> headings, int maxPerColumn) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        List<HashMap<String, Object>> currentColumnItems = new ArrayList<>();
        int currentCount = 0;

        for (HashMap<String, Object> heading : headings) {
            List<HashMap<String, Object>> pages = pagesOf(heading);

            if (pages == null) {
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
                    node = new HashMap<>(heading);
                    node.put("items", chunk);
                } else {
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
     * HORIZONTAL layout: no cap is applied. Each level-3 heading becomes
     * its own column, in order, regardless of its page count.
     */
    private static List<HashMap<String, Object>> packHeadingsHorizontally(
            List<HashMap<String, Object>> headings) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        for (HashMap<String, Object> heading : headings) {
            columns.add(buildColumn(new ArrayList<>(List.of(heading))));
        }
        return columns;
    }

    /**
     * Recursively walks a list of sibling nodes under a given level-1
     * item. All column containers found among the given siblings have
     * their headings combined into a single sequence, then arranged into
     * columns per the level-1 item's layout (horizontal or vertical). New
     * columns are inserted at the position of the first column container
     * found; non-column siblings keep their relative order and are
     * recursed into so column containers at deeper levels are also found.
     */
    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> processChildren(
            List<HashMap<String, Object>> children, int maxPerColumn, boolean horizontal) {

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
            } else {
                HashMap<String, Object> clone = new HashMap<>(child);
                Object nestedItemsObj = child.get("items");
                if (nestedItemsObj instanceof List) {
                    clone.put("items", processChildren((List<HashMap<String, Object>>) nestedItemsObj, maxPerColumn, horizontal));
                }
                result.add(clone);
            }
        }

        if (!combinedHeadings.isEmpty()) {
            List<HashMap<String, Object>> newColumns = horizontal
                    ? packHeadingsHorizontally(combinedHeadings)
                    : packHeadingsVertically(combinedHeadings, maxPerColumn);
            result.addAll(firstColumnInsertIndex, newColumns);
        }

        return result;
    }

    /**
     * Rebuilds a single level-1 nav item, reading its "layout" field to
     * decide how column containers under it should be arranged.
     */
    @SuppressWarnings("unchecked")
    private static HashMap<String, Object> transformLevel1Item(
            HashMap<String, Object> level1Item, int maxPerColumn) {

        HashMap<String, Object> clone = new HashMap<>(level1Item);
        Object itemsObj = level1Item.get("items");
        if (itemsObj instanceof List) {
            boolean horizontal = isHorizontal(level1Item);
            clone.put("items", processChildren((List<HashMap<String, Object>>) itemsObj, maxPerColumn, horizontal));
        }
        return clone;
    }

    /**
     * Entry point: transforms the full navigation root map produced by
     * NavigationUtils.getNavigationItems() -&gt; { "items": [ ... ] }.
     * Each level-1 item's own "layout" field determines whether its
     * columns are packed vertically (capped, continuous) or horizontally
     * (one column per level-3 heading, uncapped).
     */
    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> transformNavigation(
            HashMap<String, Object> root, int maxPerColumn) {

        HashMap<String, Object> output = new HashMap<>(root);
        Object itemsObj = root.get("items");
        if (itemsObj instanceof List) {
            List<HashMap<String, Object>> level1Items = (List<HashMap<String, Object>>) itemsObj;
            List<HashMap<String, Object>> transformed = new ArrayList<>();
            for (HashMap<String, Object> item : level1Items) {
                transformed.add(transformLevel1Item(item, maxPerColumn));
            }
            output.put("items", transformed);
        }
        return output;
    }

    /** Overload using the default 16-item cap. */
    public static HashMap<String, Object> transformNavigation(HashMap<String, Object> root) {
        return transformNavigation(root, MAX_ITEMS_PER_COLUMN);
    }
}
