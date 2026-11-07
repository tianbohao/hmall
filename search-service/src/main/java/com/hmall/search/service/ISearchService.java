package com.hmall.search.service;

import com.hmall.api.dto.ItemDTO;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.domain.vo.SearchFiltersVO;

public interface ISearchService {

    PageDTO<ItemDTO> search(ItemPageQuery query);

    SearchFiltersVO filters(ItemPageQuery query);

    void saveItemById(Long itemId);

    void deleteItemById(Long itemId);
}
