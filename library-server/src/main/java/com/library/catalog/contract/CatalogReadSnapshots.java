package com.library.catalog.contract;

import java.util.List;
import java.util.Map;

/** Public book-title labels for composing already visible business records. */
public interface CatalogReadSnapshots {
    Map<Long, String> titleNames(List<Long> titleIds);
}
