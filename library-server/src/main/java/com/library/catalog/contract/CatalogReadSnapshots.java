package com.library.catalog.contract;

import com.library.catalog.contract.model.TitleId;

import com.library.contract.ModuleResult;

import java.util.List;
import java.util.Map;

/** Public book-title labels for composing already visible business records. */
public interface CatalogReadSnapshots {
    ModuleResult<Map<TitleId, String>> titleNames(List<TitleId> titleIds);
}
