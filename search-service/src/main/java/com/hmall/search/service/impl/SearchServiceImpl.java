package com.hmall.search.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.domain.vo.SearchFiltersVO;
import com.hmall.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements ISearchService {

    private static final String INDEX_NAME = "items";

    private static final String CATEGORY_AGG_NAME = "categoryAgg";

    private static final String BRAND_AGG_NAME = "brandAgg";

    /**
     * 广告商品加权分值，保证广告商品排在普通商品之前
     */
    private static final float AD_BOOST_WEIGHT = 100f;

    private static final Set<String> SORTABLE_FIELDS = new HashSet<>(Arrays.asList(
            "price", "sold", "commentCount", "isAD", "updateTime"
    ));

    private final RestHighLevelClient restHighLevelClient;

    private final ItemClient itemClient;

    private final ObjectMapper objectMapper;

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        int from = validateAndGetFrom(query);
        try {
            SearchRequest request = new SearchRequest(INDEX_NAME);
            request.source()
                    .query(buildFunctionScoreQuery(query))
                    .from(from)
                    .size(query.getPageSize())
                    .trackTotalHits(true);

            // 未指定排序字段时按相关性打分排序，广告商品会通过function_score加权排在前面
            String sortField = toEsSortField(query.getSortBy());
            if (sortField != null) {
                request.source().sort(
                        sortField,
                        Boolean.TRUE.equals(query.getIsAsc()) ? SortOrder.ASC : SortOrder.DESC
                );
            }

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            long total = response.getHits().getTotalHits() == null ? 0L : response.getHits().getTotalHits().value;
            long pages = total == 0 ? 0L : (total + query.getPageSize() - 1) / query.getPageSize();

            List<ItemDTO> items = new ArrayList<>(response.getHits().getHits().length);
            for (SearchHit hit : response.getHits().getHits()) {
                ItemDoc doc = objectMapper.readValue(hit.getSourceAsString(), ItemDoc.class);
                items.add(toItemDTO(doc));
            }
            return new PageDTO<>(total, pages, items);
        } catch (IOException e) {
            log.error("商品搜索失败", e);
            throw new BizIllegalException("商品搜索失败", e);
        }
    }

    @Override
    public SearchFiltersVO filters(ItemPageQuery query) {
        try {
            SearchRequest request = new SearchRequest(INDEX_NAME);
            request.source()
                    .query(buildBasicQuery(query))
                    .size(0)
                    .aggregation(AggregationBuilders.terms(CATEGORY_AGG_NAME).field("category").size(100))
                    .aggregation(AggregationBuilders.terms(BRAND_AGG_NAME).field("brand").size(100));

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            return new SearchFiltersVO(
                    parseTermsAggregation(response, CATEGORY_AGG_NAME),
                    parseTermsAggregation(response, BRAND_AGG_NAME)
            );
        } catch (IOException e) {
            log.error("搜索过滤条件聚合失败", e);
            throw new BizIllegalException("搜索过滤条件聚合失败", e);
        }
    }

    @Override
    public void saveItemById(Long itemId) {
        ItemDTO item = itemClient.queryItemById(itemId);
        if (item == null) {
            log.warn("查询商品失败，跳过索引更新，商品id：{}", itemId);
            return;
        }
        if (item.getStatus() != null && item.getStatus() != 1) {
            deleteItemById(itemId);
            return;
        }
        try {
            ItemDoc doc = toItemDoc(item);
            IndexRequest request = new IndexRequest(INDEX_NAME)
                    .id(doc.getId())
                    .source(objectMapper.writeValueAsString(doc), XContentType.JSON);
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
            log.debug("商品索引更新成功，商品id：{}", itemId);
        } catch (IOException e) {
            log.error("商品索引更新失败，商品id：{}", itemId, e);
            throw new BizIllegalException("商品索引更新失败", e);
        }
    }

    @Override
    public void deleteItemById(Long itemId) {
        try {
            DeleteRequest request = new DeleteRequest(INDEX_NAME, String.valueOf(itemId));
            restHighLevelClient.delete(request, RequestOptions.DEFAULT);
            log.debug("商品索引删除成功，商品id：{}", itemId);
        } catch (IOException e) {
            log.error("商品索引删除失败，商品id：{}", itemId, e);
            throw new BizIllegalException("商品索引删除失败", e);
        }
    }

    private int validateAndGetFrom(ItemPageQuery query) {
        Integer pageNo = query.getPageNo();
        Integer pageSize = query.getPageSize();
        if (pageNo == null || pageSize == null || pageNo < 1 || pageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
        long from = (long) (pageNo - 1) * pageSize;
        if (from + pageSize > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多只支持查询前10000条商品");
        }
        return (int) from;
    }

    private QueryBuilder buildFunctionScoreQuery(ItemPageQuery query) {
        BoolQueryBuilder boolQuery = buildBasicQuery(query);
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = {
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        QueryBuilders.termQuery("isAD", true),
                        ScoreFunctionBuilders.weightFactorFunction(AD_BOOST_WEIGHT)
                )
        };
        return QueryBuilders.functionScoreQuery(boolQuery, functions)
                .boostMode(CombineFunction.SUM);
    }

    private BoolQueryBuilder buildBasicQuery(ItemPageQuery query) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StrUtil.isNotBlank(query.getKey())) {
            boolQuery.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            boolQuery.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (query.getMinPrice() != null || query.getMaxPrice() != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (query.getMinPrice() != null) {
                rangeQuery.gte(query.getMinPrice());
            }
            if (query.getMaxPrice() != null) {
                rangeQuery.lte(query.getMaxPrice());
            }
            boolQuery.filter(rangeQuery);
        }
        if (!boolQuery.hasClauses()) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }
        return boolQuery;
    }

    private List<String> parseTermsAggregation(SearchResponse response, String aggName) {
        List<String> values = new ArrayList<>();
        Terms terms = response.getAggregations().get(aggName);
        if (terms == null) {
            return values;
        }
        for (Terms.Bucket bucket : terms.getBuckets()) {
            values.add(bucket.getKeyAsString());
        }
        return values;
    }

    private String toEsSortField(String sortBy) {
        if (StrUtil.isBlank(sortBy)) {
            return null;
        }
        String field = sortBy;
        if ("update_time".equals(sortBy)) {
            field = "updateTime";
        } else if ("comment_count".equals(sortBy)) {
            field = "commentCount";
        }
        return SORTABLE_FIELDS.contains(field) ? field : null;
    }

    private ItemDoc toItemDoc(ItemDTO item) {
        ItemDoc doc = new ItemDoc();
        doc.setId(String.valueOf(item.getId()));
        doc.setName(item.getName());
        doc.setPrice(item.getPrice());
        doc.setImage(item.getImage());
        doc.setCategory(item.getCategory());
        doc.setBrand(item.getBrand());
        doc.setSold(item.getSold());
        doc.setCommentCount(item.getCommentCount());
        doc.setIsAD(item.getIsAD());
        doc.setUpdateTime(LocalDateTime.now());
        return doc;
    }

    private ItemDTO toItemDTO(ItemDoc doc) {
        ItemDTO item = new ItemDTO();
        if (StrUtil.isNotBlank(doc.getId())) {
            item.setId(Long.valueOf(doc.getId()));
        }
        item.setName(doc.getName());
        item.setPrice(doc.getPrice());
        item.setImage(doc.getImage());
        item.setCategory(doc.getCategory());
        item.setBrand(doc.getBrand());
        item.setSold(doc.getSold());
        item.setCommentCount(doc.getCommentCount());
        item.setIsAD(doc.getIsAD());
        return item;
    }
}
