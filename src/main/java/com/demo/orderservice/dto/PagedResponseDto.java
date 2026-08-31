package com.demo.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponseDto<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private int totalCount;
    private int totalPages;
}
