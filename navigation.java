package com.seagate.brandportal.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NavigationColumnUtils {

    private static final int MAX_ITEMS_PER_COLUMN = 16;
    private static final String LAYOUT_KEY = "layout";
    private static final String LAYOUT_HORIZONTAL = "horizontal";

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

    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> pagesOf(HashMap<String, Object> heading) {
        Object itemsObj = heading.get("items");
        return (itemsObj instanceof List) ? (List<HashMap<String, Object>>) itemsObj : null;
    }

    private static int countPages(HashMap<String, Object> heading) {
        List<HashMap<String, Object>> pages = pagesOf(heading);
        return (pages != null) ? pages.size() : 1;
    }

    private static HashMap<String, Object> buildColumn(List<HashMap<String, Object>> headings) {
        HashMap<String, Object> column = new HashMap<>();
        column.put("title", "");
        column.put("link", "");
        column.put("target", false);
        column.put("displayLeftNavigation", false);
        column.put("items", new ArrayList<>(headings));
        return column;
    }

    private static List<HashMap<String, Object>> packHeadingsVertically(
            List<HashMap<String, Object>> headings, int maxPerColumn) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        List<HashMap<String, Object>> currentColumnItems = new ArrayList<>();
        int currentCount = 0;

        for (HashMap<String, Object> heading : headings) {
            int headingCount = countPages(heading);

            if (headingCount > maxPerColumn) {
                if (!currentColumnItems.isEmpty()) {
                    columns.add(buildColumn(currentColumnItems));
                    currentColumnItems = new ArrayList<>();
                    currentCount = 0;
                }
                columns.add(buildColumn(new ArrayList<>(List.of(heading))));
                continue;
            }

            if (!currentColumnItems.isEmpty() && currentCount + headingCount > maxPerColumn) {
                columns.add(buildColumn(currentColumnItems));
                currentColumnItems = new ArrayList<>();
                currentCount = 0;
            }

            currentColumnItems.add(heading);
            currentCount += headingCount;
        }

        if (!currentColumnItems.isEmpty()) {
            columns.add(buildColumn(currentColumnItems));
        }

        return columns;
    }

    private static List<HashMap<String, Object>> packHeadingsHorizontally(
            List<HashMap<String, Object>> headings) {

        List<HashMap<String, Object>> columns = new ArrayList<>();
        for (HashMap<String, Object> heading : headings) {
            columns.add(buildColumn(new ArrayList<>(List.of(heading))));
        }
        return columns;
    }

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

    public static HashMap<String, Object> transformNavigation(HashMap<String, Object> root) {
        return transformNavigation(root, MAX_ITEMS_PER_COLUMN);
    }
}
